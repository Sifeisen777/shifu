package com.shifu.service.impl;

import cn.hutool.core.util.StrUtil;
import com.shifu.dto.CommentDTO;
import com.shifu.dto.Result;
import com.shifu.dto.UserDTO;
import com.shifu.entity.NoteComment;
import com.shifu.entity.User;
import com.shifu.mapper.NoteCommentMapper;
import com.shifu.service.INoteCommentService;
import com.shifu.service.INoteService;
import com.shifu.service.IUserService;
import com.shifu.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 李勇
 * @since 2021-12-22
 */
@Service
public class NoteCommentServiceImpl extends ServiceImpl<NoteCommentMapper, NoteComment> implements INoteCommentService {

    /** 单页最多返回的评论数，评论区只展示最新内容 */
    private static final int MAX_COMMENTS = 50;

    /** 单条评论长度上限，与表字段容量保持一致 */
    private static final int MAX_CONTENT_LENGTH = 200;

    @Resource
    private IUserService userService;

    @Resource
    private INoteService noteService;

    @Override
    public Result queryByNoteId(Long noteId) {
        List<NoteComment> comments = query()
                .eq("note_id", noteId)
                // status 为 0 表示正常，历史数据可能为 NULL，一并视为可见
                .and(w -> w.isNull("status").or().eq("status", false))
                .orderByDesc("create_time")
                .last("LIMIT " + MAX_COMMENTS)
                .list();
        if (comments.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 批量取作者，避免逐条查询
        Set<Long> userIds = comments.stream().map(NoteComment::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<CommentDTO> result = comments.stream().map(comment -> {
            CommentDTO dto = new CommentDTO();
            dto.setId(comment.getId());
            dto.setUserId(comment.getUserId());
            dto.setContent(comment.getContent());
            dto.setCreateTime(comment.getCreateTime());
            User author = userMap.get(comment.getUserId());
            if (author != null) {
                dto.setNickName(author.getNickName());
                dto.setIcon(author.getIcon());
            }
            return dto;
        }).collect(Collectors.toList());
        return Result.ok(result);
    }

    @Override
    @Transactional
    public Result saveComment(NoteComment comment) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long noteId = comment.getNoteId();
        if (noteId == null) {
            return Result.fail("笔记不存在");
        }
        String content = StrUtil.trim(comment.getContent());
        if (StrUtil.isBlank(content)) {
            return Result.fail("说点什么吧");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            return Result.fail("评论最多 " + MAX_CONTENT_LENGTH + " 字");
        }
        if (noteService.getById(noteId) == null) {
            return Result.fail("笔记不存在");
        }

        NoteComment entity = new NoteComment();
        entity.setUserId(user.getId());
        entity.setNoteId(noteId);
        // 一级评论固定为 0，暂不支持楼中楼
        entity.setParentId(0L);
        entity.setAnswerId(0L);
        entity.setContent(content);
        entity.setLiked(0);
        entity.setStatus(false);
        save(entity);

        // 计数与评论表保持同步，历史值为空时按 0 处理
        noteService.update()
                .setSql("comments = coalesce(comments, 0) + 1")
                .eq("id", noteId)
                .update();
        return Result.ok(entity.getId());
    }
}
