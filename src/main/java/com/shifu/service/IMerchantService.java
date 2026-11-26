package com.shifu.service;

import com.shifu.dto.Result;
import com.shifu.entity.Merchant;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 李勇
 * @since 2021-12-22
 */
public interface IMerchantService extends IService<Merchant> {

    Result queryById(Long id);

    Result update(Merchant merchant);

    Result queryMerchantByType(Integer typeId, Integer current, Double x, Double y);
}
