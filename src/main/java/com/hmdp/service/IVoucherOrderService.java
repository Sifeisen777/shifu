package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    /**
     * 创建秒杀订单（幂等：同一用户同一券只会成功一次）。
     * 由 RocketMQ 消费者异步调用。
     */
    void createVoucherOrder(VoucherOrder voucherOrder);
}
