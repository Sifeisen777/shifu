package com.shifu.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shifu.entity.Benefit;
import com.shifu.entity.BenefitOrder;
import com.shifu.entity.Merchant;
import com.shifu.entity.MerchantCategory;
import com.shifu.service.IBenefitOrderService;
import com.shifu.service.IBenefitService;
import com.shifu.service.IMerchantCategoryService;
import com.shifu.service.IMerchantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 客服助手可调用的业务工具。
 *
 * <p>两条硬性约束：</p>
 * <ol>
 *   <li><b>全部只读</b>：不暴露下单、秒杀、退款等写操作。需要交易时引导用户回到页面操作，
 *       由原有链路保证幂等与库存原子性。</li>
 *   <li><b>身份不可由模型指定</b>：涉及「我的」数据的方法不接受 userId 参数，而是由
 *       服务端在请求线程内取得登录用户后注入本实例，杜绝越权查询他人订单。</li>
 * </ol>
 *
 * <p>另需注意：工具在模型回调线程执行，此时 {@code UserHolder} 的 ThreadLocal 已不可用，
 * 因此用户身份必须在构造时固化，不能在方法内部再取。</p>
 *
 * @author 李勇
 */
@Slf4j
public class AssistantTools {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String SECKILL_STOCK_KEY = "seckill:stock:";
    private static final int MAX_LIST_SIZE = 10;
    /** 口语修饰词，检索时忽略 */
    private static final List<String> FILLER_WORDS = List.of(
            "附近", "周边", "周围", "有什么", "什么", "好吃", "好吃的", "好吃点", "推荐", "一下",
            "帮我", "找找", "找", "的", "餐厅", "饭店", "吃的", "店");
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            com.fasterxml.jackson.databind.json.JsonMapper.builder().build();

    private final Long userId;
    private final IMerchantService merchantService;
    private final IMerchantCategoryService categoryService;
    private final IBenefitService benefitService;
    private final IBenefitOrderService benefitOrderService;
    private final StringRedisTemplate stringRedisTemplate;

    public AssistantTools(Long userId,
                          IMerchantService merchantService,
                          IMerchantCategoryService categoryService,
                          IBenefitService benefitService,
                          IBenefitOrderService benefitOrderService,
                          StringRedisTemplate stringRedisTemplate) {
        this.userId = userId;
        this.merchantService = merchantService;
        this.categoryService = categoryService;
        this.benefitService = benefitService;
        this.benefitOrderService = benefitOrderService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Tool(name = "search_merchants",
            description = "按关键字或分类检索商户，返回商户名称、id、区域、地址、人均、评分、营业时间。"
                    + "用户问「附近/周边有什么好吃的」这类泛问时不要传关键字，直接按分类或评分返回；"
                    + "只有出现具体店名、菜系（火锅/日料/烤肉）或商圈（拱宸桥/运河上街）时才传关键字。")
    public String searchMerchants(
            @ToolParam(description = "具体关键字：店名、菜系或商圈；泛问（附近、好吃、推荐）请留空", required = false) String keyword,
            @ToolParam(description = "分类 id：1 美食 2 酒店 3 休闲娱乐 等；不确定时留空", required = false) Integer typeId,
            @ToolParam(description = "返回条数，1 到 10，默认 5", required = false) Integer limit) {
        return safe("检索商户", () -> {
            int size = limit == null ? 5 : Math.min(Math.max(limit, 1), MAX_LIST_SIZE);
            String kw = meaningfulKeyword(keyword);
            List<Merchant> merchants = queryMerchants(kw, typeId, size);
            // 关键字里夹带口语词（如「好吃的火锅」）时，用去掉修饰后的词再试一次
            if (merchants.isEmpty() && kw != null) {
                String core = coreKeyword(kw);
                if (core != null && !core.equals(kw)) {
                    merchants = queryMerchants(core, typeId, size);
                }
            }
            String prefix = "";
            if (merchants.isEmpty() && kw != null) {
                // 仍无命中则退回按评分推荐，并在结果中说明，避免直接给出空白
                merchants = queryMerchants(null, typeId, size);
                prefix = "未找到与「" + kw + "」直接匹配的商户，以下为评分较高的选择：\n";
            }
            if (merchants.isEmpty()) {
                return "未检索到符合条件的商户。";
            }
            Map<Long, String> typeNames = categoryNames();
            List<Map<String, Object>> rows = new ArrayList<>(merchants.size());
            for (Merchant merchant : merchants) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("merchantId", merchant.getId());
                row.put("名称", merchant.getName());
                row.put("分类", typeNames.getOrDefault(merchant.getTypeId(), "未分类"));
                row.put("区域", merchant.getArea());
                row.put("地址", merchant.getAddress());
                row.put("人均(元)", merchant.getAvgPrice());
                row.put("评分", merchant.getScore() == null ? null : merchant.getScore() / 10.0);
                row.put("营业时间", merchant.getOpenHours());
                rows.add(row);
            }
            return prefix + toJson(rows);
        });
    }

    @Tool(name = "get_merchant_detail",
            description = "查询单个商户的详细信息：地址、营业时间、人均、评分、点评数、累计销量。"
                    + "用户对某家店追问位置或营业时间时调用。")
    public String getMerchantDetail(
            @ToolParam(description = "商户 id，来自 search_merchants 的返回值") Long merchantId) {
        return safe("查询商户详情", () -> {
            if (merchantId == null) {
                return "缺少商户 id。";
            }
            Merchant merchant = merchantService.getById(merchantId);
            if (merchant == null) {
                return "该商户不存在或已下线。";
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("merchantId", merchant.getId());
            row.put("名称", merchant.getName());
            row.put("分类", categoryNames().getOrDefault(merchant.getTypeId(), "未分类"));
            row.put("区域", merchant.getArea());
            row.put("地址", merchant.getAddress());
            row.put("人均(元)", merchant.getAvgPrice());
            row.put("评分", merchant.getScore() == null ? null : merchant.getScore() / 10.0);
            row.put("点评数", merchant.getComments());
            row.put("累计销量", merchant.getSold());
            row.put("营业时间", merchant.getOpenHours());
            return toJson(row);
        });
    }

    @Tool(name = "list_merchant_benefits",
            description = "查询某商户在售的权益卡（券）：面额、售价、类型、剩余库存与有效期。"
                    + "用户问「这家有什么优惠/券」时调用。")
    public String listMerchantBenefits(
            @ToolParam(description = "商户 id") Long merchantId) {
        return safe("查询商户权益卡", () -> {
            if (merchantId == null) {
                return "缺少商户 id。";
            }
            List<Benefit> benefits = benefitService.query()
                    .eq("merchant_id", merchantId)
                    .orderByAsc("pay_value")
                    .list();
            if (benefits.isEmpty()) {
                return "该商户当前没有在售权益卡。";
            }
            List<Map<String, Object>> rows = new ArrayList<>(benefits.size());
            for (Benefit benefit : benefits) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("benefitId", benefit.getId());
                row.put("名称", benefit.getTitle());
                row.put("副标题", benefit.getSubTitle());
                row.put("售价(元)", centsToYuan(benefit.getPayValue()));
                row.put("抵扣(元)", centsToYuan(benefit.getActualValue()));
                row.put("类型", Objects.equals(benefit.getType(), 1) ? "限时秒杀券" : "到店核销券");
                row.put("剩余库存", currentStock(benefit));
                row.put("有效期", formatRange(benefit));
                row.put("使用规则", benefit.getRules());
                rows.add(row);
            }
            return toJson(rows);
        });
    }

    @Tool(name = "list_my_benefits",
            description = "查询当前登录用户已购买的权益卡及其状态（待使用/已核销/已退款等）。"
                    + "仅在用户询问「我的券/我买的券能不能用」时调用，无需也不接受用户 id 参数。")
    public String listMyBenefits() {
        if (userId == null) {
            return "当前为游客身份，无法查询个人权益卡。如需查询，请先登录。";
        }
        return safe("查询我的权益卡", () -> {
            List<BenefitOrder> orders = benefitOrderService.query()
                    .eq("user_id", userId)
                    .orderByDesc("create_time")
                    .last("limit " + MAX_LIST_SIZE)
                    .list();
            if (orders.isEmpty()) {
                return "该账号名下暂无已购权益卡。";
            }
            Map<Long, Benefit> benefitMap = new LinkedHashMap<>();
            List<Long> ids = orders.stream().map(BenefitOrder::getBenefitId).distinct().toList();
            if (!ids.isEmpty()) {
                for (Benefit benefit : benefitService.listByIds(ids)) {
                    benefitMap.put(benefit.getId(), benefit);
                }
            }
            List<Map<String, Object>> rows = new ArrayList<>(orders.size());
            for (BenefitOrder order : orders) {
                Benefit benefit = benefitMap.get(order.getBenefitId());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("订单号", order.getId() == null ? null : order.getId().toString());
                row.put("权益卡", benefit == null ? "已下架" : benefit.getTitle());
                row.put("状态", orderStatus(order.getStatus()));
                row.put("下单时间", order.getCreateTime() == null ? null : order.getCreateTime().format(DATE));
                rows.add(row);
            }
            return toJson(rows);
        });
    }

    private List<Merchant> queryMerchants(String keyword, Integer typeId, int size) {
        QueryWrapper<Merchant> wrapper = new QueryWrapper<>();
        if (keyword != null) {
            wrapper.and(w -> w.like("name", keyword).or().like("address", keyword).or().like("area", keyword));
        }
        if (typeId != null) {
            wrapper.eq("type_id", typeId);
        }
        wrapper.orderByDesc("score").last("limit " + size);
        return merchantService.list(wrapper);
    }

    /**
     * 过滤口语化泛词：「附近」「好吃」这类词按名称检索必然无结果，
     * 作为关键字会直接把候选集打成空白，这里统一视为没有关键字。
     */
    private String meaningfulKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return coreKeyword(keyword.trim()) == null ? null : keyword.trim();
    }

    /** 去掉修饰词后的核心词，全部是泛词时返回 null */
    private String coreKeyword(String keyword) {
        String core = keyword;
        for (String filler : FILLER_WORDS) {
            core = core.replace(filler, "");
        }
        core = core.replaceAll("[\\s，,。！？!?]", "");
        return core.isEmpty() ? null : core;
    }

    private Map<Long, String> categoryNames() {
        Map<Long, String> map = new LinkedHashMap<>();
        for (MerchantCategory category : categoryService.list()) {
            map.put(category.getId(), category.getName());
        }
        return map;
    }

    /** 秒杀券的实时库存在 Redis，普通券用数据库字段 */
    private Integer currentStock(Benefit benefit) {
        if (Objects.equals(benefit.getType(), 1)) {
            try {
                String value = stringRedisTemplate.opsForValue().get(SECKILL_STOCK_KEY + benefit.getId());
                if (value != null) {
                    return Integer.parseInt(value);
                }
            } catch (Exception e) {
                log.warn("读取秒杀库存失败 benefitId={}", benefit.getId(), e);
            }
        }
        return benefit.getStock();
    }

    private String formatRange(Benefit benefit) {
        if (benefit.getBeginTime() == null || benefit.getEndTime() == null) {
            return "长期有效";
        }
        return benefit.getBeginTime().format(DATE) + " 至 " + benefit.getEndTime().format(DATE);
    }

    private String orderStatus(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 1 -> "待支付";
            case 2 -> "待使用";
            case 3 -> "已核销";
            case 4 -> "已取消";
            case 5 -> "退款中";
            case 6 -> "已退款";
            default -> "未知";
        };
    }

    /** 权益金额以分存储，展示统一按元 */
    private String centsToYuan(Long cents) {
        if (cents == null) {
            return null;
        }
        return BigDecimal.valueOf(cents).movePointLeft(2).stripTrailingZeros().toPlainString();
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String safe(String action, Supplier<String> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("工具执行失败：{}", action, e);
            return action + "失败，请稍后再试或引导用户查看页面信息。";
        }
    }
}
