package com.shifu.agent.knowledge;

/**
 * 知识库命中的片段。
 *
 * @param docId   文档标识
 * @param title   文档标题
 * @param snippet 片段正文
 * @param score   相似度得分
 * @author 李勇
 */
public record KnowledgeHit(String docId, String title, String snippet, double score) {
}
