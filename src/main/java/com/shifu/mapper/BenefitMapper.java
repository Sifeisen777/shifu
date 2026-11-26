package com.shifu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shifu.entity.Benefit;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 李勇
 * @since 2021-12-22
 */
public interface BenefitMapper extends BaseMapper<Benefit> {

    List<Benefit> queryBenefitOfMerchant(@Param("merchantId") Long merchantId);
}
