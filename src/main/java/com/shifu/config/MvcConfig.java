package com.shifu.config;

import com.shifu.utils.LoginInterceptor;
import com.shifu.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/merchant/**",
                        "/benefit/**",
                        "/merchant-category/**",
                        "/media/**",
                        // 单段路径：/note/hot 与 /note/{id}，笔记详情允许游客浏览
                        "/note/*",
                        "/user/code",
                        "/user/login",
                        // 客服助手：游客可咨询找店与规则，涉及个人订单时由工具内部判定身份
                        "/assistant/**"
                ).order(1);
        // token刷新的拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
