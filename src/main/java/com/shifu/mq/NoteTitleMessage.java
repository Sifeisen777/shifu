package com.shifu.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记标题补写事件：只带笔记 id，正文与当前标题由消费端回查，
 * 避免消息体携带正文，也保证以数据库最新内容为准。
 *
 * @author 李勇
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteTitleMessage {

    private Long noteId;
}
