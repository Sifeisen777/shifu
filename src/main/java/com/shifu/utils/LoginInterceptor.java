package com.shifu.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.regex.Pattern;

public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 游客可访问的只读接口。
     *
     * 首页、商户详情、笔记详情、他人主页、点赞用户、关注状态本身就是公开数据，
     * 登录与否只影响能否进行关注、点赞、下单等写操作。这里用正则限定为 GET，
     * 避免新增/修改/删除接口被一并放开；数字 id 的约束则挡住 /user/me、/user/sign。
     */
    private static final List<Pattern> ANONYMOUS_GET = List.of(
            Pattern.compile("^/merchant/\\d+$"),
            Pattern.compile("^/merchant/of/(type|name)$"),
            Pattern.compile("^/merchant-category/list$"),
            Pattern.compile("^/benefit/list/\\d+$"),
            Pattern.compile("^/note/hot$"),
            Pattern.compile("^/note/\\d+$"),
            Pattern.compile("^/note/likes/\\d+$"),
            Pattern.compile("^/note/of/user$"),
            Pattern.compile("^/note-comment/list/\\d+$"),
            Pattern.compile("^/user/\\d+$"),
            Pattern.compile("^/user/info/\\d+$"),
            Pattern.compile("^/follow/or/not/\\d+$")
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.判断是否需要拦截（ThreadLocal中是否有用户）
        if (UserHolder.getUser() != null) {
            // 有用户，则放行
            return true;
        }
        // 2.游客访问公开只读接口时放行，由业务按空身份处理
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            String uri = request.getRequestURI();
            for (Pattern pattern : ANONYMOUS_GET) {
                if (pattern.matcher(uri).matches()) {
                    return true;
                }
            }
        }
        // 3.其余接口需要登录
        response.setStatus(401);
        return false;
    }
}
