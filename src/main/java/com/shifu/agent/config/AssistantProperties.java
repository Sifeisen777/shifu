package com.shifu.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 客服助手配置。
 *
 * @author 李勇
 */
@Data
@Component
@ConfigurationProperties(prefix = "shifu.assistant")
public class AssistantProperties {

    /** 总开关，关闭后接口直接返回服务未开放 */
    private boolean enabled = true;

    private RateLimit rateLimit = new RateLimit();

    private Memory memory = new Memory();

    @Data
    public static class RateLimit {
        /** 窗口期内允许的提问次数 */
        private int maxRequests = 20;
        private Duration window = Duration.ofMinutes(10);
    }

    @Data
    public static class Memory {
        /** 会话记忆保留的消息条数 */
        private int maxMessages = 20;
        /** 会话过期时间 */
        private Duration ttl = Duration.ofHours(2);
    }
}
