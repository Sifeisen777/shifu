package com.shifu.service.impl;

import com.shifu.entity.Follow;
import com.shifu.service.IFeedService;
import com.shifu.service.IFollowService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.shifu.utils.RedisConstants.FEED_KEY;

/**
 * 关注流推送实现。
 * <p>
 * 粉丝量可能上万，逐条 zadd 会产生上万次网络往返。这里按批走 pipeline：
 * 一批 500 个粉丝只发一次请求，既降低 RTT 也避免单次命令过大。
 *
 * @author 李勇
 */
@Slf4j
@Service
public class FeedServiceImpl implements IFeedService {

    /** 单次 pipeline 的粉丝数 */
    private static final int BATCH_SIZE = 500;

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public int pushNoteToFollowers(Long noteId, Long authorId, long publishTime) {
        // 1.查出作者的所有粉丝（粉丝表里 follow_user_id 是被关注者）
        List<Follow> followers = followService.query()
                .eq("follow_user_id", authorId)
                .list();
        if (followers == null || followers.isEmpty()) {
            return 0;
        }

        // 2.分批 pipeline 写入各自的收件箱：ZADD feed:{userId} publishTime noteId
        byte[] member = noteId.toString().getBytes(StandardCharsets.UTF_8);
        int pushed = 0;
        for (int i = 0; i < followers.size(); i += BATCH_SIZE) {
            List<Follow> chunk = followers.subList(i, Math.min(i + BATCH_SIZE, followers.size()));
            stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (Follow follow : chunk) {
                    byte[] key = (FEED_KEY + follow.getUserId()).getBytes(StandardCharsets.UTF_8);
                    connection.zAdd(key, publishTime, member);
                }
                return null;
            });
            pushed += chunk.size();
        }

        log.info("笔记 {} 已推送到 {} 位粉丝的收件箱", noteId, pushed);
        return pushed;
    }
}
