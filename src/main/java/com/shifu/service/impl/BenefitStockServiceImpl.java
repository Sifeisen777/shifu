package com.shifu.service.impl;

import com.shifu.entity.BenefitStock;
import com.shifu.mapper.BenefitStockMapper;
import com.shifu.service.IBenefitStockService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀权益卡表，与权益卡是一对一关系 服务实现类
 * </p>
 *
 * @author 李勇
 * @since 2022-01-04
 */
@Service
public class BenefitStockServiceImpl extends ServiceImpl<BenefitStockMapper, BenefitStock> implements IBenefitStockService {

}
