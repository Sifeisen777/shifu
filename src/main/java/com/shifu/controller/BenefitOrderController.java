package com.shifu.controller;


import com.shifu.dto.Result;
import com.shifu.service.IBenefitOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 李勇
 */
@RestController
@RequestMapping("/benefit-order")
public class BenefitOrderController {

    @Resource
    private IBenefitOrderService benefitOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillBenefit(@PathVariable("id") Long benefitId) {
        return benefitOrderService.seckillBenefit(benefitId);
    }
}
