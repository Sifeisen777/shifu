package com.shifu.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shifu.dto.Result;
import com.shifu.entity.Benefit;
import com.shifu.entity.BenefitOrder;
import com.shifu.mapper.BenefitOrderMapper;
import com.shifu.mq.BenefitOrderProducer;
import com.shifu.service.IBenefitOrderService;
import com.shifu.service.IBenefitService;
import com.shifu.service.IBenefitStockService;
import com.shifu.utils.RedisConstants;
import com.shifu.utils.RedisIdWorker;
import com.shifu.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * <p>
 * 权益卡订单服务实现
 * </p>
 *
 * @author 李勇
 * @since 2021-12-22
 */
@Slf4j
@Service
public class BenefitOrderServiceImpl extends ServiceImpl<BenefitOrderMapper, BenefitOrder>
        implements IBenefitOrderService {

    @Resource
    private IBenefitStockService benefitStockService;

    @Resource
    private IBenefitService benefitService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private BenefitOrderProducer benefitOrderProducer;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 秒杀下单：Redis + Lua 原子扣减库存，成功后投递 RocketMQ 异步落库。
     * <p>
     * 注意 {@code createBenefitOrder} 与 {@code seckillBenefit} 二者不能混用：
     * 前者是消费端的落库动作，后者是请求端的资格校验与投递。
     */
    @Override
    public Result seckillBenefit(Long benefitId) {
        // 1.先做业务侧校验，避免把无效请求打到 Lua
        Benefit benefit = benefitService.getById(benefitId);
        if (benefit == null) {
            return Result.fail("权益卡不存在");
        }
        // 只有秒杀券（type=1）才走秒杀，普通券到店核销，没有线上库存
        if (benefit.getType() == null || benefit.getType() != 1) {
            return Result.fail("该权益卡不支持秒杀");
        }
        LocalDateTime now = LocalDateTime.now();
        if (benefit.getBeginTime() != null && now.isBefore(benefit.getBeginTime())) {
            return Result.fail("秒杀尚未开始");
        }
        if (benefit.getEndTime() != null && now.isAfter(benefit.getEndTime())) {
            return Result.fail("秒杀已经结束");
        }

        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        // 2.执行 Lua：校验库存与一人一单，原子扣减并写入下单标记
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                benefitId.toString(), userId.toString()
        );
        int r = result == null ? -1 : result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 3.投递消息，由消费者异步落库
        BenefitOrder benefitOrder = new BenefitOrder()
                .setId(orderId)
                .setBenefitId(benefitId)
                .setUserId(userId);
        boolean sent = benefitOrderProducer.send(benefitOrder);
        if (!sent) {
            // 3.1.投递失败要回滚 Redis 中的库存与下单标记，否则用户会「扣了库存但没订单」
            stringRedisTemplate.opsForValue().increment(RedisConstants.SECKILL_STOCK_KEY + benefitId);
            stringRedisTemplate.opsForSet().remove(RedisConstants.SECKILL_ORDER_KEY + benefitId, userId.toString());
            log.error("秒杀订单消息投递失败，已回滚，benefitId={}, userId={}", benefitId, userId);
            return Result.fail("下单失败，请稍后重试");
        }
        return Result.ok(orderId);
    }

    /**
     * 消费端落库：分布式锁 + 一人一单复查 + 扣减库存 + 写订单。
     * 由 {@code BenefitOrderConsumer} 调用，不在请求线程中执行。
     */
    @Override
    public void createBenefitOrder(BenefitOrder benefitOrder) {
        Long userId = benefitOrder.getUserId();
        Long benefitId = benefitOrder.getBenefitId();

        // 锁粒度到用户+权益卡，避免同一用户对同一张券重复落库
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = redisLock.tryLock();
        if (!isLock) {
            log.warn("获取下单锁失败，跳过本次落库，userId={}, benefitId={}", userId, benefitId);
            return;
        }

        try {
            // 1.一人一单复查（Redis 已挡一次，这里防 MQ 重投）
            Long count = query().eq("user_id", userId).eq("benefit_id", benefitId).count();
            if (count != null && count > 0) {
                log.warn("重复订单，已忽略，userId={}, benefitId={}", userId, benefitId);
                return;
            }

            // 2.扣减库存，where stock > 0 保证不会扣成负数
            boolean success = benefitStockService.update()
                    .setSql("stock = stock - 1")
                    .eq("benefit_id", benefitId).gt("stock", 0)
                    .update();
            if (!success) {
                log.warn("库存不足，落库失败，userId={}, benefitId={}", userId, benefitId);
                return;
            }

            // 3.写订单
            save(benefitOrder);
        } finally {
            if (redisLock.isLocked() && redisLock.isHeldByCurrentThread()) {
                redisLock.unlock();
            }
        }
    }
}
