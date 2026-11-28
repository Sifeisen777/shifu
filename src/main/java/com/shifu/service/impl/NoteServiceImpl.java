package com.shifu.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shifu.dto.Result;
import com.shifu.dto.ScrollResult;
import com.shifu.dto.UserDTO;
import com.shifu.entity.Note;
import com.shifu.entity.User;
import com.shifu.mapper.NoteMapper;
import com.shifu.mq.NoteFeedProducer;
import com.shifu.mq.NoteTitleProducer;
import com.shifu.service.IFeedService;
import com.shifu.service.INoteService;
import com.shifu.service.IUserService;
import com.shifu.utils.SystemConstants;
import com.shifu.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.shifu.utils.RedisConstants.NOTE_LIKED_KEY;
import static com.shifu.utils.RedisConstants.FEED_KEY;

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
public class NoteServiceImpl extends ServiceImpl<NoteMapper, Note> implements INoteService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFeedService feedService;

    @Resource
    private NoteFeedProducer noteFeedProducer;

    @Resource
    private NoteTitleProducer noteTitleProducer;

    @Override
    public Result queryHotNote(Integer current) {
        // 根据用户查询
        Page<Note> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Note> records = page.getRecords();
        // 查询用户
        records.forEach(note -> {
            this.queryNoteUser(note);
            this.isNoteLiked(note);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryNoteById(Long id) {
        // 1.查询note
        Note note = getById(id);
        if (note == null) {
            return Result.fail("笔记不存在！");
        }
        // 2.查询note有关的用户
        queryNoteUser(note);
        // 3.查询note是否被点赞
        isNoteLiked(note);
        return Result.ok(note);
    }

    private void isNoteLiked(Note note) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        // 2.判断当前登录用户是否已经点赞
        String key = "note:liked:" + note.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        note.setIsLike(score != null);
    }

    @Override
    public Result likeNote(Long id) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前登录用户是否已经点赞
        String key = NOTE_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 3.如果未点赞，可以点赞
            // 3.1.数据库点赞数 + 1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2.保存用户到Redis的set集合  zadd key value score
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4.如果已点赞，取消点赞
            // 4.1.数据库点赞数 -1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2.把用户从Redis的set集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryNoteLikes(Long id) {
        String key = NOTE_LIKED_KEY + id;
        // 1.查询top5的点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2.解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 3.根据用户id查询用户 WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id, 5, 1)
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 4.返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveNote(Note note) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        note.setUserId(user.getId());
        // 2.保存生活笔记
        boolean isSuccess = save(note);
        if (!isSuccess) {
            return Result.fail("新增笔记失败!");
        }

        // 3.投递发布事件，由消费者异步推送到粉丝收件箱。
        //   同步 for 循环在这里会随粉丝数线性变慢（一万粉丝＝一万次 Redis 写），
        //   接口会直接超时，因此改为「请求线程只投递，消费线程做扇出」。
        long publishTime = System.currentTimeMillis();
        boolean sent = noteFeedProducer.send(note.getId(), user.getId(), publishTime);
        if (!sent) {
            // MQ 不可用时降级为同步推送，保证关注流功能不失效
            log.warn("笔记发布事件投递失败，降级为同步推送，noteId={}", note.getId());
            feedService.pushNoteToFollowers(note.getId(), user.getId(), publishTime);
        }

        // 4.标题为空时补偿生成：模型响应时间不进发布链路，失败也不影响笔记本身
        String title = note.getTitle();
        if (title == null || title.trim().length() < 2) {
            noteTitleProducer.send(note.getId());
        }
        return Result.ok(note.getId());
    }

    @Override
    public Result queryNoteOfFollow(Long max, Integer offset) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.查询收件箱 ZREVRANGEBYSCORE key Max Min LIMIT offset count
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 3.非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            // 保持返回结构一致：前端按 data.list 判断是否继续翻页，无需区分 null
            ScrollResult empty = new ScrollResult();
            empty.setList(Collections.emptyList());
            empty.setOffset(0);
            empty.setMinTime(max);
            return Result.ok(empty);
        }
        // 4.解析数据：noteId、minTime（时间戳）、offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0; // 2
        int os = 1; // 2
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) { // 5 4 4 2 2
            // 4.1.获取id
            ids.add(Long.valueOf(tuple.getValue()));
            // 4.2.获取分数(时间戳）
            long time = tuple.getScore().longValue();
            if(time == minTime){
                os++;
            }else{
                minTime = time;
                os = 1;
            }
        }

        // 5.根据id查询note
        String idStr = StrUtil.join(",", ids);
        List<Note> notes = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Note note : notes) {
            // 5.1.查询note有关的用户
            queryNoteUser(note);
            // 5.2.查询note是否被点赞
            isNoteLiked(note);
        }

        // 6.封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(notes);
        r.setOffset(os);
        r.setMinTime(minTime);

        return Result.ok(r);
    }

    private void queryNoteUser(Note note) {
        Long userId = note.getUserId();
        User user = userService.getById(userId);
        note.setName(user.getNickName());
        note.setIcon(user.getIcon());
    }
}
