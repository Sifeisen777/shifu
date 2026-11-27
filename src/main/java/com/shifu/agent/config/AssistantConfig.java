package com.shifu.agent.config;

import com.shifu.agent.memory.RedisChatMemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 客服助手的模型与会话装配。
 *
 * @author 李勇
 */
@Slf4j
@Configuration
public class AssistantConfig {

    /**
     * 会话记忆：只保留最近若干条消息，底层存储走 Redis。
     */
    @Bean
    public ChatMemory assistantChatMemory(RedisChatMemoryRepository repository, AssistantProperties properties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(properties.getMemory().getMaxMessages())
                .build();
    }

    /**
     * 助手专用的 ChatClient，与业务代码解耦：模型缺失时返回 null，
     * 服务层据此降级为规则应答，不影响应用启动。
     */
    @Bean
    public ChatClient assistantChatClient(ObjectProvider<ChatModel> chatModelProvider,
                                          ChatMemory assistantChatMemory,
                                          AssistantProperties properties,
                                          org.springframework.core.io.ResourceLoader resourceLoader) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            log.warn("未检测到可用的对话模型，客服助手将以规则应答模式运行");
            return null;
        }
        String systemPrompt = loadPrompt(resourceLoader);
        log.info("客服助手已装配：模型={}，记忆窗口={} 条，限流={} 次/{}",
                chatModel.getClass().getSimpleName(),
                properties.getMemory().getMaxMessages(),
                properties.getRateLimit().getMaxRequests(),
                properties.getRateLimit().getWindow());
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(assistantChatMemory).build())
                .build();
    }

    private String loadPrompt(org.springframework.core.io.ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource("classpath:prompts/assistant-system.md");
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("客服助手系统提示词读取失败", e);
        }
    }
}
