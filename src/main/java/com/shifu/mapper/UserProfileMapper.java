package com.shifu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shifu.entity.UserProfile;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 李勇
 * @since 2021-12-24
 */
public interface UserProfileMapper extends BaseMapper<UserProfile> {

    /**
     * 以关注关系表为准重算单个用户的粉丝数与关注数。
     *
     * 计数是 biz_follow 的派生值而非独立事实，任何一次写入都重新统计，
     * 可以避免并发下自增/自减的丢失更新。upsert 保证用户尚无资料行时也能写入。
     */
    @Update("""
            INSERT INTO sys_user_profile (user_id, fans, followee)
            VALUES (#{userId},
                    (SELECT COUNT(*) FROM biz_follow WHERE follow_user_id = #{userId}),
                    (SELECT COUNT(*) FROM biz_follow WHERE user_id = #{userId}))
            ON DUPLICATE KEY UPDATE
                fans = (SELECT COUNT(*) FROM biz_follow WHERE follow_user_id = #{userId}),
                followee = (SELECT COUNT(*) FROM biz_follow WHERE user_id = #{userId})
            """)
    int recountFollowStats(@Param("userId") Long userId);

    /**
     * 需要校准的用户：参与过关注关系的，以及资料行里计数尚未归零的。
     * 第三类用于修正历史遗留的脏计数，使启动校准幂等且可收敛。
     */
    @Select("""
            SELECT user_id FROM biz_follow
            UNION
            SELECT follow_user_id FROM biz_follow
            UNION
            SELECT user_id FROM sys_user_profile WHERE fans <> 0 OR followee <> 0
            """)
    List<Long> listUsersToRecount();
}
