package com.shifu.agent.dto;

import lombok.Data;

/**
 * 标题建议入参。
 *
 * @author 李勇
 */
@Data
public class NoteTitleRequest {

    /** 笔记正文，用于提取可写的点 */
    private String content;
}
