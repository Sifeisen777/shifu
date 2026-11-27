package com.shifu.agent.controller;

import com.shifu.agent.dto.AssistantChatDTO;
import com.shifu.agent.service.AssistantService;
import com.shifu.dto.Result;
import com.shifu.dto.UserDTO;
import com.shifu.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 客服助手接口。
 *
 * @author 李勇
 */
@Slf4j
@RestController
@RequestMapping("/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private static final Pattern SESSION_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final int MAX_QUESTION_LENGTH = 500;

    private final AssistantService assistantService;

    /**
     * 前端据此决定是否展示客服入口，以及提示当前是否处于简化模式。
     */
    @GetMapping("/config")
    public Result config() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", assistantService.isEnabled());
        data.put("modelReady", assistantService.isModelReady());
        return Result.ok(data);
    }

    /**
     * 规则库诊断：不带参数返回文档清单，带 q 返回检索命中片段。
     */
    @GetMapping("/knowledge")
    public Result knowledge(@RequestParam(required = false) String q) {
        if (q == null || q.isBlank()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("documents", assistantService.knowledgeTitles());
            return Result.ok(data);
        }
        return Result.ok(assistantService.searchKnowledge(q, 5));
    }

    /**
     * 流式问答，按 SSE 逐段返回文本。
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> chat(@RequestBody AssistantChatDTO dto,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        // 关闭中间层缓冲，保证逐字下发而不是等整段生成完
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");

        String question = dto.getMessage() == null ? "" : dto.getMessage().trim();
        if (question.isEmpty()) {
            return Flux.just("请输入想咨询的问题。");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            question = question.substring(0, MAX_QUESTION_LENGTH);
        }

        UserDTO user = UserHolder.getUser();
        Long userId = user == null ? null : user.getId();
        String identity = userId != null ? "u" + userId : "ip" + clientIp(request);
        if (!assistantService.tryAcquire(identity)) {
            return Flux.just("咨询有点频繁，请稍等一会儿再问。");
        }

        return assistantService.chat(userId, normalizeSessionId(dto.getSessionId()), question);
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId != null && SESSION_PATTERN.matcher(sessionId).matches()) {
            return sessionId;
        }
        return "s" + UUID.randomUUID().toString().replace("-", "");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
