package com.shifu.mq;

import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 笔记发布事件生产者。
 * <p>
 * 发布笔记时同步粉丝推送会随粉丝数线性变慢（一万粉丝＝一万次 Redis 写），
 * 这里改为投递事件，由消费者削峰后异步推送。
 *
 * @author 李勇
 */
@Slf4j
@Component
public class NoteFeedProducer {

    @Value("${shifu.rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${shifu.rocketmq.feed.producer-group:note-feed-producer}")
    private String producerGroup;

    @Value("${shifu.rocketmq.feed.topic:note-feed-topic}")
    private String topic;

    private DefaultMQProducer producer;

    private volatile boolean started = false;

    @PostConstruct
    public void start() {
        try {
            producer = new DefaultMQProducer(producerGroup);
            producer.setNamesrvAddr(nameServer);
            // 发布笔记不能因为 MQ 抖动而卡住请求线程：
            // 缩短超时与重试，失败后走同步降级，总耗时控制在 3 秒内
            producer.setSendMsgTimeout(1200);
            producer.setRetryTimesWhenSendFailed(1);
            producer.start();
            started = true;
            log.info("笔记推送生产者启动成功，nameServer={}, topic={}", nameServer, topic);
        } catch (Exception e) {
            // 不阻断启动：MQ 不可用时调用方会降级为同步推送
            log.error("笔记推送生产者启动失败，nameServer={}，将降级为同步推送", nameServer, e);
        }
    }

    /**
     * 投递笔记发布事件。
     *
     * @return 投递成功返回 true
     */
    public boolean send(Long noteId, Long authorId, long publishTime) {
        if (!started) {
            log.warn("笔记推送生产者未就绪，noteId={}", noteId);
            return false;
        }
        NoteFeedMessage payload = new NoteFeedMessage(noteId, authorId, publishTime);
        Message message = new Message(topic, payload.getNoteId().toString(),
                JSONUtil.toJsonStr(payload).getBytes(StandardCharsets.UTF_8));
        message.setKeys("note:" + noteId);
        try {
            SendResult result = producer.send(message);
            log.debug("笔记发布事件已投递，noteId={}, msgId={}, status={}",
                    noteId, result.getMsgId(), result.getSendStatus());
            return SendStatus.SEND_OK.equals(result.getSendStatus());
        } catch (Exception e) {
            log.error("笔记发布事件投递失败，noteId={}", noteId, e);
            return false;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (producer != null) {
            producer.shutdown();
        }
    }
}
