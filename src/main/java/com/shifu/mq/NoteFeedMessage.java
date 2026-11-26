package com.shifu.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记发布事件：只带必要字段，粉丝列表由消费端回查，避免消息体随粉丝数膨胀。
 *
 * @author 李勇
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteFeedMessage {

    /** 笔记id */
    private Long noteId;

    /** 作者id */
    private Long authorId;

    /** 发布时间戳，作为收件箱 ZSet 的分数，保证不同消费者写入的顺序一致 */
    private Long publishTime;
}
