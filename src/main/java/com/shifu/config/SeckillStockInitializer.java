package com.shifu.config;

import com.shifu.entity.Benefit;
import com.shifu.entity.BenefitStock;
import com.shifu.service.IBenefitService;
import com.shifu.service.IBenefitStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.shifu.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * 秒杀库存预热。
 *
 * 库存的权威值同时存在于 MySQL 与 Redis：下单时先扣 Redis，再由消费者扣 MySQL。
 * Redis 重启或过期后库存键会消失，此时接口会因为读不到库存直接拒绝请求。
 * 启动时按数据库补写缺失的键（已存在的键不覆盖，避免抹掉两次落库之间的扣减）。
 *
 * @author 李勇
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillStockInitializer implements ApplicationRunner {

    private final IBenefitStockService benefitStockService;

    private final IBenefitService benefitService;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<BenefitStock> stocks = benefitStockService.list();
        if (stocks.isEmpty()) {
            return;
        }
        // 只预热仍在售的秒杀券，过期券保留原库存即可
        Set<Long> onSale = benefitService.query()
                .eq("type", 1)
                .eq("status", 1)
                .list()
                .stream()
                .filter(benefit -> benefit.getEndTime() == null || benefit.getEndTime().isAfter(LocalDateTime.now()))
                .map(Benefit::getId)
                .collect(Collectors.toSet());

        int restored = 0;
        for (BenefitStock stock : stocks) {
            if (!onSale.contains(stock.getBenefitId())) {
                continue;
            }
            Boolean created = stringRedisTemplate.opsForValue()
                    .setIfAbsent(SECKILL_STOCK_KEY + stock.getBenefitId(), String.valueOf(stock.getStock()));
            if (Boolean.TRUE.equals(created)) {
                restored++;
            }
        }
        if (restored > 0) {
            log.info("秒杀库存已预热，补写 {} 个库存键", restored);
        }
    }
}
