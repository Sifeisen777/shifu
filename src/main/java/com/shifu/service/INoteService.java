package com.shifu.service;

import com.shifu.dto.Result;
import com.shifu.entity.Note;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 李勇
 * @since 2021-12-22
 */
public interface INoteService extends IService<Note> {

    Result queryHotNote(Integer current);

    Result queryNoteById(Long id);

    Result likeNote(Long id);

    Result queryNoteLikes(Long id);

    Result saveNote(Note note);

    Result queryNoteOfFollow(Long max, Integer offset);

}
