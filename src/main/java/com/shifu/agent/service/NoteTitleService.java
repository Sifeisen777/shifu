package com.shifu.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 笔记标题生成。
 *
 * <p>两个入口共用同一套提示词：发布页按钮走同步调用，立即返回候选；
 * 标题为空时由消息消费端异步补写，避免把模型响应时间加进发布接口。</p>
 *
 * @author 李勇
 */
@Slf4j
@Service
public class NoteTitleService {

    private static final String RATE_KEY_PREFIX = "assistant:title:u";
    private static final String DISABLED_API_KEY = "disabled";
    private static final int MAX_CONTENT_LENGTH = 1200;
    private static final int MAX_TITLE_LENGTH = 26;
    private static final int DAILY_LIMIT = 30;

    private static final String PROMPT_TEMPLATE = """
            你是本地生活社区的内容编辑。根据下面这段到店体验正文，写出 3 个中文标题，每行一个。

            要求：
            - 每个 12 到 20 字，口语化，有画面感，能让人想点开
            - 不使用书名号、引号、表情符号，不以序号开头
            - 只输出标题本身，不要解释，不要输出「标题：」这类前缀

            正文：
            %s
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final String apiKey;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 标题生成不使用会话记忆，因此单独构建客户端：
     * 复用客服的 ChatClient 会带上记忆 Advisor，缺少 conversationId 时直接抛异常。
     */
    private volatile ChatClient titleClient;

    public NoteTitleService(ObjectProvider<ChatModel> chatModelProvider,
                            @Value("${spring.ai.openai.api-key:}") String apiKey,
                            StringRedisTemplate stringRedisTemplate) {
        this.chatModelProvider = chatModelProvider;
        this.apiKey = apiKey;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean isReady() {
        return chatModelProvider.getIfAvailable() != null
                && apiKey != null
                && !apiKey.isBlank()
                && !DISABLED_API_KEY.equals(apiKey);
    }

    private ChatClient client() {
        if (titleClient == null) {
            synchronized (this) {
                if (titleClient == null) {
                    ChatModel model = chatModelProvider.getIfAvailable();
                    if (model == null) {
                        return null;
                    }
                    titleClient = ChatClient.builder(model).build();
                }
            }
        }
        return titleClient;
    }

    /**
     * 生成标题候选，最多 3 个。模型不可用或调用失败时返回空集合，由调用方决定提示方式。
     */
    public List<String> suggest(String content) {
        ChatClient client = client();
        if (client == null || content == null || content.isBlank()) {
            return List.of();
        }
        String body = content.length() > MAX_CONTENT_LENGTH
                ? content.substring(0, MAX_CONTENT_LENGTH)
                : content;
        try {
            String raw = client
                    .prompt()
                    .user(String.format(PROMPT_TEMPLATE, body))
                    .call()
                    .content();
            return parse(raw);
        } catch (Exception e) {
            log.warn("标题生成失败，正文长度={}", body.length(), e);
            return List.of();
        }
    }

    /**
     * 取第一个候选，供异步补写使用。
     */
    public String generateOne(String content) {
        List<String> candidates = suggest(content);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * 标题生成按用户限流：一天 30 次。Redis 异常时放行，避免影响正常发布。
     */
    public boolean tryAcquire(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            String key = RATE_KEY_PREFIX + userId;
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count == null) {
                return true;
            }
            if (count == 1L) {
                stringRedisTemplate.expire(key, Duration.ofDays(1));
            }
            return count <= DAILY_LIMIT;
        } catch (Exception e) {
            log.warn("标题生成限流检查失败，本次放行，userId={}", userId, e);
            return true;
        }
    }

    /**
     * 模型输出按行解析，去掉编号、引号与多余标点，去重后保留合法长度。
     */
    private List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> titles = new LinkedHashSet<>();
        for (String line : raw.split("\\r?\\n")) {
            String title = line
                    .replaceAll("^\\s*\\d+[.、)）]\\s*", "")
                    .replaceAll("^[「『\"'“]|[」』\"'”]$", "")
                    .replaceAll("^标题[:：]\\s*", "")
                    .trim();
            if (title.length() >= 6 && title.length() <= MAX_TITLE_LENGTH && !title.contains("标题")) {
                titles.add(title);
            }
            if (titles.size() >= 3) {
                break;
            }
        }
        return new ArrayList<>(titles);
    }
}
