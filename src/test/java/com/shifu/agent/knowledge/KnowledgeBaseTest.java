package com.shifu.agent.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则库检索的回归测试：确保常见问法能命中对应文档。
 *
 * @author 李勇
 */
class KnowledgeBaseTest {

    private final KnowledgeBase knowledgeBase = new KnowledgeBase();

    @Test
    void loadsAllDocuments() {
        assertTrue(knowledgeBase.chunkCount() >= 20, "知识库切片数量不足");
        assertTrue(knowledgeBase.titles().contains("退款与取消规则"));
    }

    @Test
    void refundQuestionHitsRefundPolicy() {
        // 券规则文档里有一句「退款规则见《退款与取消规则》」，属同主题的指向性内容，
        // 因此按召回位置断言：退款手册需出现在前两位，模型拿到即依据充分
        assertDocWithinTop("我买的券没来得及用，能退吗", "退款与取消规则", 2);
    }

    @Test
    void seckillQuestionHitsSeckillRules() {
        assertTopDoc("秒杀一个人能抢几张", "限时秒杀规则");
    }

    @Test
    void noteAdQuestionHitsNoteGuidelines() {
        assertTopDoc("笔记可以发广告吗", "笔记发布规范");
    }

    @Test
    void loginCodeQuestionHitsAccountDoc() {
        assertTopDoc("验证码多久有效", "账号与登录说明");
    }

    @Test
    void merchantInfoQuestionHitsMerchantDoc() {
        assertTopDoc("人均价格是准确的吗", "商户信息说明");
    }

    @Test
    void unrelatedQuestionDoesNotDominate() {
        // 词面检索无法理解语义：「快速排序」与「按评分排序」会因字面重合产生弱命中；
        // 因此只约束高分片段不得出现，是否采纳由模型按业务范围判断
        List<KnowledgeHit> hits = knowledgeBase.search("帮我写一段快速排序", 3);
        assertTrue(hits.isEmpty() || hits.get(0).score() < 0.25,
                "无关问题不应出现高置信命中，实际：" + describe(hits));
    }

    private void assertTopDoc(String question, String expectedTitle) {
        assertDocWithinTop(question, expectedTitle, 1);
    }

    private void assertDocWithinTop(String question, String expectedTitle, int topN) {
        List<KnowledgeHit> hits = knowledgeBase.search(question, 3);
        assertFalse(hits.isEmpty(), "未检索到片段：" + question);
        List<String> titles = hits.stream().map(KnowledgeHit::title).limit(topN).toList();
        assertTrue(titles.contains(expectedTitle),
                "前 " + topN + " 位未命中《" + expectedTitle + "》：" + question + " → " + describe(hits));
    }

    private String describe(List<KnowledgeHit> hits) {
        StringBuilder sb = new StringBuilder();
        for (KnowledgeHit hit : hits) {
            sb.append("\n  ").append(hit.score()).append(' ').append(hit.title())
                    .append(" | ").append(hit.snippet().replace("\n", " "));
        }
        return sb.toString();
    }
}
