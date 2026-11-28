package com.shifu.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论展示对象：把评论与作者信息合并，避免前端二次请求。
 *
 * @author 李勇
 */
@Data
public class CommentDTO {

    private Long id;

    private Long userId;

    private String nickName;

    private String icon;

    private String content;

    private LocalDateTime createTime;
}
