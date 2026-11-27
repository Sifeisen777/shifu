package com.shifu.agent.dto;

import lombok.Data;

/**
 * 客服提问入参。
 *
 * @author 李勇
 */
@Data
public class AssistantChatDTO {

    /** 会话标识，由前端生成并复用，用于维持上下文；不传则退化为单轮问答 */
    private String sessionId;

    /** 用户问题 */
    private String message;
}
