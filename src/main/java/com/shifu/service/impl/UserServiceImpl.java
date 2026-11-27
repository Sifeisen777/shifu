package com.shifu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shifu.dto.LoginFormDTO;
import com.shifu.dto.PasswordFormDTO;
import com.shifu.dto.ProfileFormDTO;
import com.shifu.dto.Result;
import com.shifu.dto.UserDTO;
import com.shifu.entity.User;
import com.shifu.entity.UserProfile;
import com.shifu.mapper.UserMapper;
import com.shifu.service.IUserProfileService;
import com.shifu.service.IUserService;
import com.shifu.utils.PasswordHasher;
import com.shifu.utils.RegexUtils;
import com.shifu.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.shifu.utils.RedisConstants.*;
import static com.shifu.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 李勇
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserProfileService userProfileService;

    @Override
    public Result logout(String token) {
        // 删除 Redis 中的登录令牌，使当前 token 立即失效（而不是仅在前端清除）
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        }
        UserHolder.removeUser();
        return Result.ok();
    }

    @Override
    public Result updateProfile(ProfileFormDTO form, String token) {
        UserDTO current = UserHolder.getUser();
        if (current == null) {
            return Result.fail("请先登录");
        }
        Long userId = current.getId();

        // 1.昵称与头像存在 sys_user
        String nickName = StrUtil.blankToDefault(form.getNickName(), current.getNickName());
        User user = new User();
        user.setId(userId);
        user.setNickName(nickName);
        if (StrUtil.isNotBlank(form.getIcon())) {
            user.setIcon(form.getIcon());
        }
        updateById(user);

        // 2.其余资料存在 sys_user_profile，不存在则新建（主键即用户id）
        UserProfile profile = userProfileService.getById(userId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
        }
        profile.setIntroduce(form.getIntroduce());
        profile.setCity(form.getCity());
        profile.setGender(form.getGender());
        profile.setBirthday(form.getBirthday());
        userProfileService.saveOrUpdate(profile);

        // 3.同步 Redis 中的登录用户缓存，否则本次会话取到的仍是旧昵称
        current.setNickName(nickName);
        if (StrUtil.isNotBlank(form.getIcon())) {
            current.setIcon(form.getIcon());
        }
        UserHolder.saveUser(current);
        if (StrUtil.isNotBlank(token)) {
            String tokenKey = LOGIN_USER_KEY + token;
            Map<String, Object> patch = new HashMap<>(2);
            patch.put("nickName", nickName);
            if (StrUtil.isNotBlank(form.getIcon())) {
                patch.put("icon", form.getIcon());
            }
            stringRedisTemplate.opsForHash().putAll(tokenKey, patch);
        }
        return Result.ok();
    }

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("发送短信验证码成功，验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        String code = loginForm.getCode();
        String password = loginForm.getPassword();
        if (StrUtil.isBlank(code) && StrUtil.isBlank(password)) {
            return Result.fail("请提供验证码或密码");
        }
        if (StrUtil.isNotBlank(password) && (password.length() < 6 || password.length() > 32)) {
            return Result.fail("密码长度需为 6~32 位");
        }

        User user;
        if (StrUtil.isNotBlank(code)) {
            // 验证码登录：未注册的手机号自动创建账号
            String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
            if (cacheCode == null || !cacheCode.equals(code)) {
                return Result.fail("验证码错误");
            }
            user = query().eq("phone", phone).one();
            if (user == null) {
                // 注册时可直接设置初始密码，省去一次单独设置
                user = createUserWithPhone(phone, password);
            }
        } else {
            // 密码登录：账号不存在与密码错误使用同一提示，避免暴露账号是否已注册
            user = query().eq("phone", phone).one();
            if (user == null || StrUtil.isBlank(user.getPassword())
                    || !PasswordHasher.matches(password, user.getPassword())) {
                return Result.fail("手机号或密码错误");
            }
        }
        return Result.ok(issueToken(user));
    }

    @Override
    public Result setPassword(PasswordFormDTO form) {
        UserDTO current = UserHolder.getUser();
        if (current == null) {
            return Result.fail("请先登录");
        }
        String password = form == null ? null : form.getPassword();
        if (StrUtil.isBlank(password) || password.length() < 6 || password.length() > 32) {
            return Result.fail("密码长度需为 6~32 位");
        }
        User user = getById(current.getId());
        if (user == null) {
            return Result.fail("用户不存在");
        }
        // 登录态本身是凭证，但修改已有密码仍要求验证原密码，防止会话被冒用后直接改密
        if (StrUtil.isNotBlank(user.getPassword())) {
            String oldPassword = form.getOldPassword();
            if (StrUtil.isBlank(oldPassword) || !PasswordHasher.matches(oldPassword, user.getPassword())) {
                return Result.fail("原密码不正确");
            }
        }
        User update = new User();
        update.setId(user.getId());
        update.setPassword(PasswordHasher.hash(password));
        updateById(update);
        return Result.ok();
    }

    /**
     * 发放登录令牌并写入 Redis 会话，验证码登录与密码登录共用。
     */
    private String issueToken(User user) {
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return token;
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_CHECKIN_KEY + userId + keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_CHECKIN_KEY + userId + keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        int count = 0;
        while (true) {
            if ((num & 1) == 0) {
                break;
            }
            count++;
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone, String password) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        if (StrUtil.isNotBlank(password)) {
            user.setPassword(PasswordHasher.hash(password));
        }
        save(user);
        return user;
    }
}
