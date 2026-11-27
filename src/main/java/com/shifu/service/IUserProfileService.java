package com.shifu.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shifu.entity.UserProfile;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 李勇
 * @since 2021-12-24
 */
public interface IUserProfileService extends IService<UserProfile> {

    /**
     * 按当前关注关系重算该用户的粉丝数与关注数。
     * @param userId 用户 id
     */
    void recountFollowStats(Long userId);
}
