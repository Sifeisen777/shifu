package com.shifu.config;

import com.shifu.mapper.UserProfileMapper;
import com.shifu.service.IUserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时按关注关系校准粉丝数与关注数。
 *
 * 这两个字段是从 biz_follow 派生的冗余计数，早期版本只写关系表、不回写计数，
 * 导致已有数据的计数恒为 0，且无法通过增量更新自行收敛。这里以关系表为真值
 * 全量重算一次；写入逻辑同样采用重算，因此重复执行结果一致。
 *
 * @author 李勇
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowCountInitializer implements ApplicationRunner {

    private final UserProfileMapper userProfileMapper;
    private final IUserProfileService userProfileService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Long> userIds = userProfileMapper.listUsersToRecount();
            for (Long userId : userIds) {
                userProfileService.recountFollowStats(userId);
            }
            if (!userIds.isEmpty()) {
                log.info("关注关系计数已校准，共 {} 个用户", userIds.size());
            }
        } catch (Exception e) {
            // 校准失败不影响应用启动，写入逻辑会持续修正计数
            log.warn("关注关系计数校准失败，将在下次启动重试：{}", e.getMessage());
        }
    }
}
