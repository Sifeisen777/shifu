package com.shifu.config;

import com.shifu.entity.Merchant;
import com.shifu.service.IMerchantService;
import com.shifu.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 启动时把商户坐标同步到 Redis GEO。
 *
 * 课程原版依赖手工执行测试类写入 GEO 索引，索引丢失后「按距离排序」会查不到任何商户。
 * 这里在应用启动时幂等补齐：key 已存在则跳过，Redis 不可用只告警不阻断启动。
 *
 * @author 李勇
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantGeoInitializer implements ApplicationRunner {

    private final IMerchantService merchantService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Merchant> merchants = merchantService.list();
            Map<Long, List<Merchant>> grouped = merchants.stream()
                    .filter(m -> m.getX() != null && m.getY() != null && m.getTypeId() != null)
                    .collect(Collectors.groupingBy(Merchant::getTypeId));

            int total = 0;
            for (Map.Entry<Long, List<Merchant>> entry : grouped.entrySet()) {
                String key = RedisConstants.MERCHANT_GEO_KEY + entry.getKey();
                // 已初始化过的不重复写入，避免每次启动覆盖
                if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                    continue;
                }
                List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
                for (Merchant merchant : entry.getValue()) {
                    locations.add(new RedisGeoCommands.GeoLocation<>(
                            merchant.getId().toString(),
                            new Point(merchant.getX(), merchant.getY())
                    ));
                }
                Long added = stringRedisTemplate.opsForGeo().add(key, locations);
                total += added == null ? 0 : added.intValue();
            }
            if (total > 0) {
                log.info("商户地理位置索引已初始化，共写入 {} 条", total);
            }
        } catch (Exception e) {
            log.warn("商户地理位置初始化失败，距离排序将回退为数据库查询：{}", e.getMessage());
        }
    }
}
