package com.shifu.mq;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shifu.agent.service.NoteTitleService;
import com.shifu.entity.Note;
import com.shifu.service.INoteService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 笔记标题补写消费者。
 *
 * <p>只处理标题为空或过短的笔记；回写时带条件更新，避免覆盖用户同时手动填写的标题。</p>
 *
 * @author 李勇
 */
@Slf4j
@Component
public class NoteTitleConsumer implements MessageListenerConcurrently {

    /** 少于该长度视为未填写有效标题 */
    private static final int MIN_TITLE_LENGTH = 2;
    private static final int MAX_START_ATTEMPTS = 8;
    private static final int RETRY_DELAY_SECONDS = 15;

    @Value("${shifu.rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${shifu.rocketmq.title.consumer-group:note-title-consumer}")
    private String consumerGroup;

    @Value("${shifu.rocketmq.title.topic:note-title-topic}")
    private String topic;

    @Resource
    private INoteService noteService;

    @Resource
    private NoteTitleService noteTitleService;

    private DefaultMQPushConsumer consumer;

    /** 主题由首条消息创建，启动阶段可能尚不存在，因此失败后定时重试 */
    private final ScheduledExecutorService starter = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "note-title-consumer-starter");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    public void start() {
        scheduleStart(0);
    }

    private void scheduleStart(int attempt) {
        long delay = attempt == 0 ? 0 : RETRY_DELAY_SECONDS;
        starter.schedule(() -> doStart(attempt), delay, TimeUnit.SECONDS);
    }

    private void doStart(int attempt) {
        try {
            consumer = new DefaultMQPushConsumer(consumerGroup);
            consumer.setNamesrvAddr(nameServer);
            consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
            consumer.subscribe(topic, "*");
            consumer.setConsumeThreadMin(1);
            consumer.setConsumeThreadMax(2);
            consumer.registerMessageListener(this);
            consumer.start();
            log.info("标题补写消费者启动成功，topic={}, group={}", topic, consumerGroup);
        } catch (Exception e) {
            shutdownQuietly();
            if (attempt + 1 >= MAX_START_ATTEMPTS) {
                log.error("标题补写消费者启动失败，已重试 {} 次，空标题将不会自动补写", MAX_START_ATTEMPTS, e);
                return;
            }
            log.warn("标题补写消费者启动失败，{} 秒后重试（{}/{}）：{}",
                    RETRY_DELAY_SECONDS, attempt + 1, MAX_START_ATTEMPTS, e.getMessage());
            scheduleStart(attempt + 1);
        }
    }

    private void shutdownQuietly() {
        try {
            if (consumer != null) {
                consumer.shutdown();
            }
        } catch (Exception ignored) {
            // 关闭失败不影响重试
        } finally {
            consumer = null;
        }
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            try {
                NoteTitleMessage payload = JSONUtil.toBean(
                        new String(msg.getBody(), StandardCharsets.UTF_8), NoteTitleMessage.class);
                fillTitle(payload.getNoteId());
            } catch (Exception e) {
                // 单条失败不影响后续消息；标题补写属于增强能力，不重投以免反复消耗模型额度
                log.warn("标题补写处理失败，msgId={}", msg.getMsgId(), e);
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    private void fillTitle(Long noteId) {
        if (noteId == null) {
            return;
        }
        Note note = noteService.getById(noteId);
        if (note == null) {
            log.warn("标题补写跳过：笔记不存在，noteId={}", noteId);
            return;
        }
        String current = note.getTitle();
        if (current != null && current.trim().length() >= MIN_TITLE_LENGTH) {
            // 已有人工标题，尊重用户输入
            return;
        }
        if (note.getContent() == null || note.getContent().isBlank()) {
            return;
        }
        String title = noteTitleService.generateOne(note.getContent());
        if (title == null || title.isBlank()) {
            log.warn("标题补写未获得结果，noteId={}", noteId);
            return;
        }
        // 条件更新：仅当标题仍为空时写入，避免覆盖并发填写
        boolean updated = noteService.update(new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, noteId)
                .and(w -> w.isNull(Note::getTitle).or().eq(Note::getTitle, "").or()
                        .apply("char_length(trim(title)) < {0}", MIN_TITLE_LENGTH))
                .set(Note::getTitle, title));
        log.info("标题补写完成 noteId={} title={} updated={}", noteId, title, updated);
    }

    @PreDestroy
    public void shutdown() {
        starter.shutdownNow();
        shutdownQuietly();
    }
}
