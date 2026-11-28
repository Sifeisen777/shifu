package com.shifu.controller;


import com.shifu.dto.Result;
import com.shifu.entity.NoteComment;
import com.shifu.service.INoteCommentService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 李勇
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/note-comment")
public class NoteCommentController {

    @Resource
    private INoteCommentService noteCommentService;

    /**
     * 笔记的最新评论，游客可读
     */
    @GetMapping("/list/{noteId}")
    public Result list(@PathVariable("noteId") Long noteId) {
        return noteCommentService.queryByNoteId(noteId);
    }

    /**
     * 发布评论，需登录
     */
    @PostMapping
    public Result save(@RequestBody NoteComment comment) {
        return noteCommentService.saveComment(comment);
    }
}
