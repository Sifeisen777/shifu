package com.hmdp.mq;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
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

/**
 * 秒杀订单消费者：从 RocketMQ 拉取订单消息，真正落库。
 *
 * @author 李勇
 */
@Slf4j
@Component
public class SeckillOrderConsumer implements MessageListenerConcurrently {

    @Value("${hmdp.rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${hmdp.rocketmq.consumer-group:seckill-order-consumer}")
    private String consumerGroup;

    @Value("${hmdp.rocketmq.topic:seckill-order-topic}")
    private String topic;

    @Resource
    private IVoucherOrderService voucherOrderService;

    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void start() {
        try {
            consumer = new DefaultMQPushConsumer(consumerGroup);
            consumer.setNamesrvAddr(nameServer);
            consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
            consumer.subscribe(topic, "*");
            consumer.setConsumeThreadMin(1);
            consumer.setConsumeThreadMax(4);
            consumer.registerMessageListener(this);
            consumer.start();
            log.info("RocketMQ 消费者启动成功，topic={}, group={}", topic, consumerGroup);
        } catch (Exception e) {
            log.error("RocketMQ 消费者启动失败，nameServer={}，秒杀订单将无法落库", nameServer, e);
        }
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            try {
                VoucherOrder order = JSONUtil.toBean(
                        new String(msg.getBody(), StandardCharsets.UTF_8), VoucherOrder.class);
                voucherOrderService.createVoucherOrder(order);
            } catch (Exception e) {
                // 返回 RECONSUME_LATER，broker 会按退避策略重投，最多 16 次后进入死信队列
                log.error("消费秒杀订单失败，msgId={}", msg.getMsgId(), e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    @PreDestroy
    public void shutdown() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
