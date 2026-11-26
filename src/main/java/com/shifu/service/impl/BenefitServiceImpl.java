package com.shifu.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shifu.dto.Result;
import com.shifu.entity.BenefitStock;
import com.shifu.entity.Benefit;
import com.shifu.mapper.BenefitMapper;
import com.shifu.service.IBenefitStockService;
import com.shifu.service.IBenefitService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;

import static com.shifu.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 李勇
 * @since 2021-12-22
 */
@Service
public class BenefitServiceImpl extends ServiceImpl<BenefitMapper, Benefit> implements IBenefitService {

    @Resource
    private IBenefitStockService seckillBenefitService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBenefitOfMerchant(Long merchantId) {
        // 查询权益卡信息
        List<Benefit> benefits = getBaseMapper().queryBenefitOfMerchant(merchantId);
        // 返回结果
        return Result.ok(benefits);
    }

    @Override
    @Transactional
    public void addBenefitStock(Benefit benefit) {
        // 保存权益卡
        save(benefit);
        // 保存秒杀信息
        BenefitStock seckillBenefit = new BenefitStock();
        seckillBenefit.setBenefitId(benefit.getId());
        seckillBenefit.setStock(benefit.getStock());
        seckillBenefit.setBeginTime(benefit.getBeginTime());
        seckillBenefit.setEndTime(benefit.getEndTime());
        seckillBenefitService.save(seckillBenefit);
        // 保存秒杀库存到Redis中
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + benefit.getId(), benefit.getStock().toString());
    }
}
