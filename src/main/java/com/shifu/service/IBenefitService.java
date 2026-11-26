package com.shifu.service;

import com.shifu.dto.Result;
import com.shifu.entity.Benefit;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 李勇
 * @since 2021-12-22
 */
public interface IBenefitService extends IService<Benefit> {

    Result queryBenefitOfMerchant(Long merchantId);

    void addBenefitStock(Benefit benefit);
}
