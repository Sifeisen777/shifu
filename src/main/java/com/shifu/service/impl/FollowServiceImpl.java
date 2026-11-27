package com.shifu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shifu.dto.Result;
import com.shifu.dto.UserDTO;
import com.shifu.entity.Follow;
import com.shifu.mapper.FollowMapper;
import com.shifu.service.IFollowService;
import com.shifu.service.IUserProfileService;
import com.shifu.service.IUserService;
import com.shifu.utils.UserHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 李勇
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Resource
    private IUserProfileService userProfileService;

    @Override
    @Transactional
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        if (userId.equals(followUserId)) {
            return Result.fail("不能关注自己");
        }
        String key = "follows:" + userId;

        if (Boolean.TRUE.equals(isFollow)) {
            // 已关注直接返回，重复点击不会让关系表多出记录，也不会重复计数
            Long exists = query().eq("user_id", userId)
                    .eq("follow_user_id", followUserId).count();
            if (exists > 0) {
                return Result.ok();
            }
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            try {
                save(follow);
            } catch (DuplicateKeyException e) {
                // 并发下由 (user_id, follow_user_id) 唯一索引兜底，视为已关注
                return Result.ok();
            }
            stringRedisTemplate.opsForSet().add(key, followUserId.toString());
        } else {
            // remove 返回是否真的删除了记录，未关注时的取关不触发计数回写
            boolean removed = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
            if (!removed) {
                return Result.ok();
            }
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
        }

        // 计数是关系表的派生值，重算而非增减，避免并发下丢失更新
        userProfileService.recountFollowStats(userId);
        userProfileService.recountFollowStats(followUserId);
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        String key2 = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
