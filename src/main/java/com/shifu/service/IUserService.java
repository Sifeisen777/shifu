package com.shifu.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shifu.dto.LoginFormDTO;
import com.shifu.dto.ProfileFormDTO;
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

    /**
     * 退出登录：删除 Redis 中的 token，使当前令牌立即失效
     * @param token 请求头中的登录令牌
     */
    Result logout(String token);

    /**
     * 更新个人资料（昵称、头像、简介、城市、性别、生日）
     * @param form 资料表单
     * @param token 当前登录令牌，用于同步 Redis 中的用户缓存
     */
    Result updateProfile(ProfileFormDTO form, String token);

}
