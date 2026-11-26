package com.shifu.service;

import com.shifu.dto.Result;
import com.shifu.entity.BenefitOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 李勇
 * @since 2021-12-22
 */
public interface IBenefitOrderService extends IService<BenefitOrder> {

    Result seckillBenefit(Long benefitId);

    /**
     * 创建秒杀订单（幂等：同一用户同一券只会成功一次）。
     * 由 RocketMQ 消费者异步调用。
     */
    void createBenefitOrder(BenefitOrder benefitOrder);
}
