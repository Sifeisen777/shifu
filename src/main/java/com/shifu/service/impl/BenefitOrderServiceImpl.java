package com.shifu.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shifu.dto.Result;
import com.shifu.entity.BenefitOrder;
import com.shifu.mapper.BenefitOrderMapper;
import com.shifu.mq.BenefitOrderProducer;
import com.shifu.service.IBenefitStockService;
import com.shifu.service.IBenefitOrderService;
import com.shifu.utils.RedisConstants;
import com.shifu.utils.RedisIdWorker;
import com.shifu.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 李勇
 * @since 2021-12-22
 */
@Slf4j
@Service
public class BenefitOrderServiceImpl extends ServiceImpl<BenefitOrderMapper, BenefitOrder> implements IBenefitOrderService {

    @Resource
    private IBenefitStockService seckillBenefitService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private BenefitOrderProducer seckillOrderProducer;


    /*private BlockingQueue<BenefitOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private class BenefitOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    // 1.获取队列中的订单信息
                    BenefitOrder benefitOrder = orderTasks.take();
                    // 2.创建订单
                    createBenefitOrder(benefitOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }*/

    @Override
    public void createBenefitOrder(BenefitOrder benefitOrder) {
        Long userId = benefitOrder.getUserId();
        Long benefitId = benefitOrder.getBenefitId();
        // 创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if (!isLock) {
            // 获取锁失败，直接返回失败或者重试
            log.error("不允许重复下单！");
            return;
        }

        try {
            // 5.1.查询订单
            Long count = query().eq("user_id", userId).eq("benefit_id", benefitId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                log.error("不允许重复下单！");
                return;
            }

            // 6.扣减库存
            boolean success = seckillBenefitService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("benefit_id", benefitId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                log.error("库存不足！");
                return;
            }

            // 7.创建订单
            save(benefitOrder);
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }

    @Override
    public Result seckillBenefit(Long benefitId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                benefitId.toString(), userId.toString()
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 3.发送消息到 RocketMQ，由消费者异步落库
        BenefitOrder benefitOrder = new BenefitOrder()
                .setId(orderId)
                .setBenefitId(benefitId)
                .setUserId(userId);
        boolean sent = seckillOrderProducer.send(benefitOrder);
        if (!sent) {
            // 3.1.消息发送失败，回滚 Redis 中的库存与下单标记，让用户可以重试
            stringRedisTemplate.opsForValue().increment(RedisConstants.SECKILL_STOCK_KEY + benefitId);
            stringRedisTemplate.opsForSet().remove(RedisConstants.SECKILL_ORDER_KEY + benefitId, userId.toString());
            log.error("秒杀订单消息发送失败，已回滚，benefitId={}, userId={}", benefitId, userId);
            return Result.fail("下单失败，请稍后重试");
        }
        // 4.返回订单id
        return Result.ok(orderId);
    }

    /*@Override
    public Result seckillBenefit(Long benefitId) {
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                benefitId.toString(), userId.toString()
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2.为0 ，有购买资格，把下单信息保存到阻塞队列
        BenefitOrder benefitOrder = new BenefitOrder();
        // 2.3.订单id
        long orderId = redisIdWorker.nextId("order");
        benefitOrder.setId(orderId);
        // 2.4.用户id
        benefitOrder.setUserId(userId);
        // 2.5.代金券id
        benefitOrder.setBenefitId(benefitId);
        // 2.6.放入阻塞队列
        orderTasks.add(benefitOrder);

        // 3.返回订单id
        return Result.ok(orderId);
    }*/
    /*@Override
    public Result seckillBenefit(Long benefitId) {
        // 1.查询权益卡
        BenefitStock benefit = seckillBenefitService.getById(benefitId);
        // 2.判断秒杀是否开始
        if (benefit.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (benefit.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀已经结束！");
        }
        // 4.判断库存是否充足
        if (benefit.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }

        return createBenefitOrder(benefitId);
    }



    @Transactional
    public Result createBenefitOrder(Long benefitId) {
        // 5.一人一单
        Long userId = UserHolder.getUser().getId();

        // 创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if(!isLock){
            // 获取锁失败，直接返回失败或者重试
            return Result.fail("不允许重复下单！");
        }

        try {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("benefit_id", benefitId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 6.扣减库存
            boolean success = seckillBenefitService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("benefit_id", benefitId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }

            // 7.创建订单
            BenefitOrder benefitOrder = new BenefitOrder();
            // 7.1.订单id
            long orderId = redisIdWorker.nextId("order");
            benefitOrder.setId(orderId);
            // 7.2.用户id
            benefitOrder.setUserId(userId);
            // 7.3.代金券id
            benefitOrder.setBenefitId(benefitId);
            save(benefitOrder);

            // 7.返回订单id
            return Result.ok(orderId);
        } finally {
            // 释放锁
            redisLock.unlock();
        }

    }*/
    /*@Transactional
    public Result createBenefitOrder(Long benefitId) {
        // 5.一人一单
        Long userId = UserHolder.getUser().getId();

        // 创建锁对象
        SimpleRedisLock redisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock(1200);
        // 判断
        if(!isLock){
            // 获取锁失败，直接返回失败或者重试
            return Result.fail("不允许重复下单！");
        }

        try {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("benefit_id", benefitId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 6.扣减库存
            boolean success = seckillBenefitService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("benefit_id", benefitId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }

            // 7.创建订单
            BenefitOrder benefitOrder = new BenefitOrder();
            // 7.1.订单id
            long orderId = redisIdWorker.nextId("order");
            benefitOrder.setId(orderId);
            // 7.2.用户id
            benefitOrder.setUserId(userId);
            // 7.3.代金券id
            benefitOrder.setBenefitId(benefitId);
            save(benefitOrder);

            // 7.返回订单id
            return Result.ok(orderId);
        } finally {
            // 释放锁
            redisLock.unlock();
        }

    }*/

    /*@Transactional
    public Result createBenefitOrder(Long benefitId) {
        // 5.一人一单
        Long userId = UserHolder.getUser().getId();

        synchronized (userId.toString().intern()) {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("benefit_id", benefitId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 6.扣减库存
            boolean success = seckillBenefitService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("benefit_id", benefitId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }

            // 7.创建订单
            BenefitOrder benefitOrder = new BenefitOrder();
            // 7.1.订单id
            long orderId = redisIdWorker.nextId("order");
            benefitOrder.setId(orderId);
            // 7.2.用户id
            benefitOrder.setUserId(userId);
            // 7.3.代金券id
            benefitOrder.setBenefitId(benefitId);
            save(benefitOrder);

            // 7.返回订单id
            return Result.ok(orderId);
        }
    }*/
}
