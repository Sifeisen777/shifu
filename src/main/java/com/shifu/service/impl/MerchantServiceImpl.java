package com.shifu.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shifu.dto.Result;
import com.shifu.entity.Merchant;
import com.shifu.mapper.MerchantMapper;
import com.shifu.service.IMerchantService;
import com.shifu.utils.CacheClient;
import com.shifu.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.shifu.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 李勇
 * @since 2021-12-22
 */
@Service
public class MerchantServiceImpl extends ServiceImpl<MerchantMapper, Merchant> implements IMerchantService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
        Merchant merchant = cacheClient
                .queryWithPassThrough(CACHE_MERCHANT_KEY, id, Merchant.class, this::getById, CACHE_MERCHANT_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        // Merchant merchant = cacheClient
        //         .queryWithMutex(CACHE_MERCHANT_KEY, id, Merchant.class, this::getById, CACHE_MERCHANT_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿
        // Merchant merchant = cacheClient
        //         .queryWithLogicalExpire(CACHE_MERCHANT_KEY, id, Merchant.class, this::getById, 20L, TimeUnit.SECONDS);

        if (merchant == null) {
            return Result.fail("商户不存在！");
        }
        // 7.返回
        return Result.ok(merchant);
    }

    @Override
    @Transactional
    public Result update(Merchant merchant) {
        Long id = merchant.getId();
        if (id == null) {
            return Result.fail("商户id不能为空");
        }
        // 1.更新数据库
        updateById(merchant);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_MERCHANT_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryMerchantByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            return queryByDb(typeId, current);
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：merchantId、distance
        String key = MERCHANT_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list =
                results == null ? Collections.emptyList() : results.getContent();

        // 4.附近没有命中（索引未初始化或超出半径）时回退数据库查询，避免列表空白
        if (list.isEmpty()) {
            return current == 1 ? queryByDb(typeId, current) : Result.ok(Collections.emptyList());
        }
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取商户id
            String merchantIdStr = result.getContent().getName();
            ids.add(Long.valueOf(merchantIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(merchantIdStr, distance);
        });
        // 5.根据id查询Merchant
        String idStr = StrUtil.join(",", ids);
        List<Merchant> merchants = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Merchant merchant : merchants) {
            merchant.setDistance(distanceMap.get(merchant.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(merchants);
    }

    /**
     * 按类型分页查询（不涉及距离）。坐标查询无结果时作为兜底。
     */
    private Result queryByDb(Integer typeId, Integer current) {
        Page<Merchant> page = query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }
}
