package com.shifu.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shifu.dto.LoginFormDTO;
import com.shifu.dto.Result;
import com.shifu.entity.User;

import jakarta.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 李勇
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();

}
