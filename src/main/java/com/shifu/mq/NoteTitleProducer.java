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
 * 笔记标题补写事件生产者。
 *
 * <p>发布接口只负责投递，模型响应时间不再进入请求链路；
 * 投递失败说明 MQ 不可用，此时放弃补写，笔记以空标题发布，用户仍可自行编辑。</p>
 *
 * @author 李勇
 */
@Slf4j
@Component
public class NoteTitleProducer {

    @Value("${shifu.rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${shifu.rocketmq.title.producer-group:note-title-producer}")
    private String producerGroup;

    @Value("${shifu.rocketmq.title.topic:note-title-topic}")
    private String topic;

    private DefaultMQProducer producer;

    private volatile boolean started = false;

    @PostConstruct
    public void start() {
        try {
            producer = new DefaultMQProducer(producerGroup);
            producer.setNamesrvAddr(nameServer);
            producer.setSendMsgTimeout(1200);
            producer.setRetryTimesWhenSendFailed(1);
            producer.start();
            started = true;
            log.info("标题补写生产者启动成功，nameServer={}, topic={}", nameServer, topic);
        } catch (Exception e) {
            log.error("标题补写生产者启动失败，nameServer={}，空标题将不会自动补写", nameServer, e);
        }
    }

    /**
     * 投递标题补写事件。
     *
     * @return 投递成功返回 true
     */
    public boolean send(Long noteId) {
        if (!started) {
            log.warn("标题补写生产者未就绪，noteId={}", noteId);
            return false;
        }
        NoteTitleMessage payload = new NoteTitleMessage(noteId);
        Message message = new Message(topic, noteId.toString(),
                JSONUtil.toJsonStr(payload).getBytes(StandardCharsets.UTF_8));
        message.setKeys("note-title:" + noteId);
        try {
            SendResult result = producer.send(message);
            return SendStatus.SEND_OK.equals(result.getSendStatus());
        } catch (Exception e) {
            log.error("标题补写事件投递失败，noteId={}", noteId, e);
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
