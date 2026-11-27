package com.shifu.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shifu.entity.UserProfile;
import com.shifu.mapper.UserProfileMapper;
import com.shifu.service.IUserProfileService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 李勇
 * @since 2021-12-24
 */
@Service
public class UserProfileServiceImpl extends ServiceImpl<UserProfileMapper, UserProfile> implements IUserProfileService {

    @Override
    public void recountFollowStats(Long userId) {
        baseMapper.recountFollowStats(userId);
    }
}
