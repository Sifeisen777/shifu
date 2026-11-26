package com.shifu.mq;

import cn.hutool.json.JSONUtil;
import com.shifu.entity.BenefitOrder;
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
 * 秒杀订单生产者：把下单请求投递到 RocketMQ，由消费者异步落库。
 *
 * @author 李勇
 */
@Slf4j
@Component
public class BenefitOrderProducer {

    @Value("${shifu.rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${shifu.rocketmq.producer-group:benefit-order-producer}")
    private String producerGroup;

    @Value("${shifu.rocketmq.topic:benefit-order-topic}")
    private String topic;

    private DefaultMQProducer producer;

    private volatile boolean started = false;

    @PostConstruct
    public void start() {
        try {
            producer = new DefaultMQProducer(producerGroup);
            producer.setNamesrvAddr(nameServer);
            // 发送超时时间，默认 3s；秒杀场景要快失败
            producer.setSendMsgTimeout(3000);
            // 发送失败重试次数（同步发送默认 2 次）
            producer.setRetryTimesWhenSendFailed(2);
            producer.start();
            started = true;
            log.info("RocketMQ 生产者启动成功，nameServer={}, topic={}", nameServer, topic);
        } catch (Exception e) {
            // 不阻断应用启动：MQ 没起来时只是秒杀不可用，其余功能照常
            log.error("RocketMQ 生产者启动失败，nameServer={}，秒杀功能将不可用", nameServer, e);
        }
    }

    /**
     * 发送秒杀订单消息（同步发送，保证拿到 broker 确认）。
     *
     * @return 发送成功返回 true
     */
    public boolean send(BenefitOrder order) {
        if (!started) {
            log.error("RocketMQ 生产者未就绪，订单 {} 无法投递", order.getId());
            return false;
        }
        Message message = new Message(topic, JSONUtil.toJsonStr(order).getBytes(StandardCharsets.UTF_8));
        // 用订单号作为业务 key，方便在控制台按订单号查消息
        message.setKeys(order.getId().toString());
        try {
            SendResult result = producer.send(message);
            log.debug("秒杀订单消息已投递，orderId={}, msgId={}, status={}",
                    order.getId(), result.getMsgId(), result.getSendStatus());
            return SendStatus.SEND_OK.equals(result.getSendStatus());
        } catch (Exception e) {
            log.error("秒杀订单消息投递失败，orderId={}", order.getId(), e);
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
