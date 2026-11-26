package com.shifu.controller;


import com.shifu.dto.Result;
import com.shifu.entity.Benefit;
import com.shifu.service.IBenefitService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 李勇
 */
@RestController
@RequestMapping("/benefit")
public class BenefitController {

    @Resource
    private IBenefitService benefitService;

    /**
     * 新增秒杀券
     * @param benefit 权益卡信息，包含秒杀信息
     * @return 权益卡id
     */
    @PostMapping("seckill")
    public Result addBenefitStock(@RequestBody Benefit benefit) {
        benefitService.addBenefitStock(benefit);
        return Result.ok(benefit.getId());
    }

    /**
     * 新增普通券
     * @param benefit 权益卡信息
     * @return 权益卡id
     */
    @PostMapping
    public Result addBenefit(@RequestBody Benefit benefit) {
        benefitService.save(benefit);
        return Result.ok(benefit.getId());
    }


    /**
     * 查询商户的权益卡列表
     * @param merchantId 商户id
     * @return 权益卡列表
     */
    @GetMapping("/list/{merchantId}")
    public Result queryBenefitOfMerchant(@PathVariable("merchantId") Long merchantId) {
       return benefitService.queryBenefitOfMerchant(merchantId);
    }
}
