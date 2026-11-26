package com.shifu.mq;

import cn.hutool.json.JSONUtil;
import com.shifu.service.IFeedService;
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
 * 笔记发布事件消费者：异步把笔记推送到粉丝收件箱。
 *
 * @author 李勇
 */
@Slf4j
@Component
public class NoteFeedConsumer implements MessageListenerConcurrently {

    @Value("${shifu.rocketmq.name-server:127.0.0.1:9876}")
    private String nameServer;

    @Value("${shifu.rocketmq.feed.consumer-group:note-feed-consumer}")
    private String consumerGroup;

    @Value("${shifu.rocketmq.feed.topic:note-feed-topic}")
    private String topic;

    @Resource
    private IFeedService feedService;

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
            log.info("笔记推送消费者启动成功，topic={}, group={}", topic, consumerGroup);
        } catch (Exception e) {
            log.error("笔记推送消费者启动失败，nameServer={}，关注流将不会自动推送", nameServer, e);
        }
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            try {
                NoteFeedMessage payload = JSONUtil.toBean(
                        new String(msg.getBody(), StandardCharsets.UTF_8), NoteFeedMessage.class);
                feedService.pushNoteToFollowers(
                        payload.getNoteId(), payload.getAuthorId(), payload.getPublishTime());
            } catch (Exception e) {
                // 推送失败交给 broker 重投；重复推送只会覆盖 ZSet 同分成员，不会产生重复内容
                log.error("消费笔记发布事件失败，msgId={}", msg.getMsgId(), e);
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
