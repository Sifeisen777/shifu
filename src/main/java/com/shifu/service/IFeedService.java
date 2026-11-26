package com.shifu.service;

/**
 * 关注流（Feed）推送。
 *
 * @author 李勇
 */
public interface IFeedService {

    /**
     * 把新笔记写入作者所有粉丝的收件箱。
     * <p>
     * 由 MQ 消费者调用；MQ 不可用时由发布笔记的请求线程降级调用。
     *
     * @param noteId      笔记id
     * @param authorId    作者id
     * @param publishTime 发布时间戳，作为 ZSet 分数
     * @return 实际写入的粉丝数
     */
    int pushNoteToFollowers(Long noteId, Long authorId, long publishTime);
}
