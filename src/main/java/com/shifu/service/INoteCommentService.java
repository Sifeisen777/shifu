package com.shifu.service;

import com.shifu.dto.Result;
import com.shifu.entity.NoteComment;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 李勇
 * @since 2021-12-22
 */
public interface INoteCommentService extends IService<NoteComment> {

    /**
     * 查询一条笔记的最新评论，附作者昵称与头像
     */
    Result queryByNoteId(Long noteId);

    /**
     * 发布评论，并同步笔记的评论数
     */
    Result saveComment(NoteComment comment);
}
