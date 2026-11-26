package com.shifu.controller;


import com.shifu.dto.Result;
import com.shifu.entity.MerchantCategory;
import com.shifu.service.IMerchantCategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 李勇
 */
@RestController
@RequestMapping("/merchant-category")
public class MerchantCategoryController {
    @Resource
    private IMerchantCategoryService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        List<MerchantCategory> typeList = typeService
                .query().orderByAsc("sort").list();
        return Result.ok(typeList);
    }
}
