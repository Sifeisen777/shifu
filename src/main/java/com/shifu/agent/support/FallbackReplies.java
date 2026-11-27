package com.shifu.agent.support;

import java.util.List;

/**
 * 模型不可用时的规则应答。
 *
 * <p>客服入口是常驻的，模型超时、额度耗尽或网络故障时不能直接白屏，
 * 这里按关键字命中常见问题给出确定性答复，并说明当前处于简化模式。</p>
 *
 * @author 李勇
 */
public final class FallbackReplies {

    private static final String PREFIX = "（智能问答暂时不可用，以下为常见问题说明）\n\n";

    private static final List<Rule> RULES = List.of(
            new Rule(List.of("退款", "退货", "退钱"),
                    "已购权益卡如需退款，请先联系门店确认；已核销的券不支持退款。"
                            + "未核销的订单可在「我的」页查看状态，如状态异常请在门店页面提交反馈。"),
            new Rule(List.of("秒杀", "抢", "库存", "卖完", "抢光"),
                    "限时秒杀券有固定库存和购买时间窗，售完或超出时间窗即无法购买，同一账号对同一张券限购一张。"
                            + "商户详情页会显示当前是否可抢。"),
            new Rule(List.of("券", "优惠", "折扣", "权益"),
                    "打开商户详情页可以看到该店在售的权益卡，包含售价、抵扣金额、有效期与使用规则。"
                            + "已购的券在「我的」页查看，到店出示券码由门店核销。"),
            new Rule(List.of("营业", "几点", "开门", "关门", "时间"),
                    "营业时间以商户详情页的展示为准。如果页面没有标注，建议直接联系门店确认。"),
            new Rule(List.of("地址", "在哪", "位置", "怎么走", "停车"),
                    "商户详情页提供完整地址与所在区域。平台不提供导航，建议复制地址后使用地图应用。"),
            new Rule(List.of("登录", "验证码", "注册", "账号"),
                    "使用手机号加验证码登录，验证码两分钟内有效，超时可重新获取。首次登录会自动创建账号。"),
            new Rule(List.of("发布", "笔记", "发帖", "写"),
                    "在「发布」页上传图片、填写标题与正文即可发布笔记，也可以关联到具体商户，方便其他食客参考。")
    );

    private FallbackReplies() {
    }

    public static String answer(String question) {
        if (question != null) {
            for (Rule rule : RULES) {
                for (String keyword : rule.keywords()) {
                    if (question.contains(keyword)) {
                        return PREFIX + rule.answer();
                    }
                }
            }
        }
        return PREFIX + "你可以问我：某类店有哪些推荐、某家店的位置与营业时间、这家店有什么优惠券、"
                + "以及已购券的使用与退款规则。也可以直接在首页搜索商户名称。";
    }

    private record Rule(List<String> keywords, String answer) {
    }
}
