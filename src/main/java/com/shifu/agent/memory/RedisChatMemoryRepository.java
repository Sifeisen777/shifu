package com.shifu.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shifu.agent.config.AssistantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于 Redis List 的会话记忆存储。
 *
 * <p>每个会话对应一个 List，元素是序列化后的单条消息，随会话过期自动回收。
 * 复用业务已有的 Redis，不额外引入存储组件。</p>
 *
 * @author 李勇
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "assistant:memory:";

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    private final AssistantProperties properties;

    @Override
    public List<String> findConversationIds() {
        // 会话清理由过期时间负责，无需全量枚举
        return Collections.emptyList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<String> raw = stringRedisTemplate.opsForList().range(key(conversationId), 0, -1);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<Message> messages = new ArrayList<>(raw.size());
        for (String item : raw) {
            Message message = deserialize(item);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String key = key(conversationId);
        stringRedisTemplate.delete(key);
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<String> items = new ArrayList<>(messages.size());
        for (Message message : messages) {
            String text = message.getText();
            // 工具调用与工具返回属于中间过程，不进入长期记忆，避免上下文膨胀
            if (text == null || text.isBlank() || message.getMessageType() == MessageType.TOOL) {
                continue;
            }
            items.add(serialize(message.getMessageType(), text));
        }
        if (items.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForList().rightPushAll(key, items);
        stringRedisTemplate.expire(key, properties.getMemory().getTtl());
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        stringRedisTemplate.delete(key(conversationId));
    }

    private String key(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    private String serialize(MessageType type, String text) {
        try {
            return objectMapper.writeValueAsString(new StoredMessage(type.getValue(), text));
        } catch (Exception e) {
            log.warn("会话消息序列化失败，跳过该条消息", e);
            return null;
        }
    }

    private Message deserialize(String item) {
        try {
            StoredMessage stored = objectMapper.readValue(item, StoredMessage.class);
            if (stored == null || stored.text() == null) {
                return null;
            }
            MessageType type = MessageType.fromValue(stored.type());
            return switch (type) {
                case USER -> new UserMessage(stored.text());
                case ASSISTANT -> new AssistantMessage(stored.text());
                case SYSTEM -> new SystemMessage(stored.text());
                default -> null;
            };
        } catch (Exception e) {
            log.warn("会话消息反序列化失败，跳过该条消息", e);
            return null;
        }
    }

    private record StoredMessage(String type, String text) {
    }
}
