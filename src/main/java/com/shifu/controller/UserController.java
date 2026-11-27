package com.shifu.controller;


import cn.hutool.core.bean.BeanUtil;
import com.shifu.dto.LoginFormDTO;
import com.shifu.dto.PasswordFormDTO;
import com.shifu.dto.ProfileFormDTO;
import com.shifu.dto.Result;
import com.shifu.dto.UserDTO;
import com.shifu.entity.User;
import com.shifu.entity.UserProfile;
import com.shifu.service.IUserProfileService;
import com.shifu.service.IUserService;
import com.shifu.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 李勇
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserProfileService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能：验证码登录与密码登录共用入口，按表单中携带的凭据分流
     * @param loginForm 登录参数，包含手机号，以及验证码或密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.login(loginForm, session);
    }

    /**
     * 设置或修改登录密码（需已登录）
     */
    @PutMapping("/password")
    public Result setPassword(@RequestBody PasswordFormDTO form) {
        return userService.setPassword(form);
    }

    /**
     * 登出功能：删除服务端令牌，token 立即失效
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "authorization", required = false) String token) {
        return userService.logout(token);
    }

    /**
     * 更新个人资料
     * @param form 资料表单
     */
    @PutMapping("/info")
    public Result updateProfile(
            @RequestBody ProfileFormDTO form,
            @RequestHeader(value = "authorization", required = false) String token) {
        return userService.updateProfile(form, token);
    }

    @GetMapping("/me")
    public Result me(){
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserProfile info = userInfoService.getById(userId);
        if (info == null) {
            // 尚未填写过资料时返回零值对象，前端无需区分「无资料」与「计数为 0」
            UserProfile empty = new UserProfile();
            empty.setUserId(userId);
            empty.setFans(0);
            empty.setFollowee(0);
            return Result.ok(empty);
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }
}