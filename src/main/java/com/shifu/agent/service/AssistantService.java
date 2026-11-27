package com.shifu.agent.service;

import com.shifu.agent.config.AssistantProperties;
import com.shifu.agent.knowledge.KnowledgeBase;
import com.shifu.agent.knowledge.KnowledgeHit;
import com.shifu.agent.support.FallbackReplies;
import com.shifu.agent.tools.AssistantTools;
import com.shifu.service.IBenefitOrderService;
import com.shifu.service.IBenefitService;
import com.shifu.service.IMerchantCategoryService;
import com.shifu.service.IMerchantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 客服助手编排：限流、工具装配、流式回答与降级。
 *
 * @author 李勇
 */
@Slf4j
@Service
public class AssistantService {

    private static final String RATE_KEY_PREFIX = "assistant:rate:";
    private static final String DISABLED_API_KEY = "disabled";

    private final AssistantProperties properties;
    private final ObjectProvider<ChatClient> chatClientProvider;
    private final String apiKey;
    private final StringRedisTemplate stringRedisTemplate;
    private final IMerchantService merchantService;
    private final IMerchantCategoryService merchantCategoryService;
    private final IBenefitService benefitService;
    private final IBenefitOrderService benefitOrderService;
    private final KnowledgeBase knowledgeBase;

    public AssistantService(AssistantProperties properties,
                            ObjectProvider<ChatClient> chatClientProvider,
                            @Value("${spring.ai.openai.api-key:}") String apiKey,
                            StringRedisTemplate stringRedisTemplate,
                            IMerchantService merchantService,
                            IMerchantCategoryService merchantCategoryService,
                            IBenefitService benefitService,
                            IBenefitOrderService benefitOrderService,
                            KnowledgeBase knowledgeBase) {
        this.properties = properties;
        this.chatClientProvider = chatClientProvider;
        this.apiKey = apiKey;
        this.stringRedisTemplate = stringRedisTemplate;
        this.merchantService = merchantService;
        this.merchantCategoryService = merchantCategoryService;
        this.benefitService = benefitService;
        this.benefitOrderService = benefitOrderService;
        this.knowledgeBase = knowledgeBase;
    }

    /** 规则库检索，供诊断接口与工具复用 */
    public List<KnowledgeHit> searchKnowledge(String query, int topK) {
        return knowledgeBase.search(query, topK);
    }

    public List<String> knowledgeTitles() {
        return knowledgeBase.titles();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** 是否具备真实模型能力；未配置密钥时只提供规则应答 */
    public boolean isModelReady() {
        return isEnabled()
                && chatClientProvider.getIfAvailable() != null
                && apiKey != null
                && !apiKey.isBlank()
                && !DISABLED_API_KEY.equals(apiKey);
    }

    /**
     * 滑动窗口限流。Redis 异常时放行，避免基础设施抖动直接导致客服不可用。
     */
    public boolean tryAcquire(String identity) {
        String key = RATE_KEY_PREFIX + identity;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count == null) {
                return true;
            }
            if (count == 1L) {
                stringRedisTemplate.expire(key, properties.getRateLimit().getWindow());
            }
            return count <= properties.getRateLimit().getMaxRequests();
        } catch (Exception e) {
            log.warn("限流检查失败，本次请求放行", e);
            return true;
        }
    }

    /**
     * 流式问答。
     *
     * @param userId    登录用户 id，游客为 null；由请求线程取得，不接受前端传入
     * @param sessionId 会话标识，用于拼接上下文
     * @param question  用户问题
     */
    public Flux<String> chat(Long userId, String sessionId, String question) {
        if (!isEnabled()) {
            return Flux.just("客服助手暂未开放，如需帮助请在「我的」页提交反馈。");
        }
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null || !isModelReady()) {
            log.info("客服助手处于规则应答模式，session={}", sessionId);
            return Flux.just(FallbackReplies.answer(question));
        }

        AssistantTools tools = new AssistantTools(userId, merchantService, merchantCategoryService,
                benefitService, benefitOrderService, stringRedisTemplate, knowledgeBase);

        StringBuilder answer = new StringBuilder();
        AtomicReference<String> usage = new AtomicReference<>("未采集");
        long start = System.currentTimeMillis();

        try {
            return chatClient.prompt()
                    .user(question)
                    .tools(tools)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .stream()
                    .chatResponse()
                    .doOnNext(response -> {
                        String current = readUsage(response);
                        if (current != null) {
                            usage.set(current);
                        }
                    })
                    .map(this::textOf)
                    .filter(text -> !text.isEmpty())
                    // 模型按 token 逐段返回，中文常见单字成段；合并后下发，减少 SSE 帧数
                    .bufferTimeout(8, java.time.Duration.ofMillis(120))
                    .map(chunks -> String.join("", chunks))
                    .filter(text -> !text.isEmpty())
                    .doOnNext(answer::append)
                    .doOnComplete(() -> log.info("客服回答完成 session={} userId={} 字数={} 耗时={}ms 用量={}",
                            sessionId, userId, answer.length(), System.currentTimeMillis() - start, usage.get()))
                    .onErrorResume(e -> {
                        log.warn("模型调用失败，降级为规则应答 session={} 原因={}", sessionId, e.getMessage());
                        return Flux.just(FallbackReplies.answer(question));
                    });
        } catch (Exception e) {
            log.warn("模型调用初始化失败，降级为规则应答 session={}", sessionId, e);
            return Flux.just(FallbackReplies.answer(question));
        }
    }

    private String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private String readUsage(ChatResponse response) {
        try {
            if (response == null || response.getMetadata() == null) {
                return null;
            }
            Usage usage = response.getMetadata().getUsage();
            if (usage == null || usage.getPromptTokens() == null) {
                return null;
            }
            return "prompt=" + usage.getPromptTokens() + ",completion=" + usage.getCompletionTokens();
        } catch (Exception e) {
            return null;
        }
    }
}
