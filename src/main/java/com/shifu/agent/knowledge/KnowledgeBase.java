package com.shifu.agent.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 政策类知识库：加载 Markdown 文档、切分片段并建立稀疏索引。
 *
 * <p>检索采用中文二元组 + TF-IDF 余弦相似度，而不是向量检索：当前接入的模型没有
 * 向量化接口，政策类文档只有十余篇，稀疏检索在无额外依赖、无额外调用成本的前提下
 * 已能稳定命中，且命中结果可解释——这对需要「说明依据」的客服场景更合适。
 * 若要扩展到长文档或语义相近但用词不同的问法，可在本类背后替换为向量库实现。</p>
 *
 * @author 李勇
 */
@Slf4j
@Component
public class KnowledgeBase {

    private static final String LOCATION_PATTERN = "classpath*:knowledge/*.md";
    /** 片段按小节切分，过长段落再按句子拆开，保证命中片段的针对性 */
    private static final int CHUNK_MAX_CHARS = 160;
    private static final int CHUNK_MIN_CHARS = 24;
    private static final int TOP_LIMIT_PER_DOC = 2;
    private static final double MIN_SCORE = 0.06;
    /** 出现频率过高的词项（虚词、通用词）不参与打分 */
    private static final double MAX_TERM_DOC_RATIO = 0.4;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9]+|[\\u4e00-\\u9fa5]+");

    private final List<Chunk> chunks = new ArrayList<>();
    private final Map<String, Double> idf = new HashMap<>();
    private final Map<String, Integer> termDocFreq = new HashMap<>();
    private final Map<String, Set<String>> titleTokens = new HashMap<>();
    private final List<String> titles = new ArrayList<>();

    public KnowledgeBase() {
        load();
    }

    public int chunkCount() {
        return chunks.size();
    }

    public List<String> titles() {
        return List.copyOf(titles);
    }

    /**
     * 检索与问题最相关的片段。
     *
     * @param query 用户问题
     * @param topK  返回条数上限
     */
    public List<KnowledgeHit> search(String query, int topK) {
        if (query == null || query.isBlank() || chunks.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> queryTf = termFrequency(tokenize(query));
        if (queryTf.isEmpty()) {
            return List.of();
        }
        Map<String, Double> queryVector = new HashMap<>();
        double queryNorm = 0;
        int maxDocFreq = (int) Math.floor(chunks.size() * MAX_TERM_DOC_RATIO);
        for (Map.Entry<String, Integer> entry : queryTf.entrySet()) {
            Double weight = idf.get(entry.getKey());
            if (weight == null) {
                continue;
            }
            // 「可以」「什么」这类词几乎每篇都有，参与打分只会引入噪声
            if (termDocFreq.getOrDefault(entry.getKey(), 0) > maxDocFreq) {
                continue;
            }
            double value = (1 + Math.log(entry.getValue())) * weight * termBoost(entry.getKey());
            queryVector.put(entry.getKey(), value);
            queryNorm += value * value;
        }
        if (queryNorm == 0) {
            return List.of();
        }
        queryNorm = Math.sqrt(queryNorm);

        List<Chunk> scored = new ArrayList<>();
        for (Chunk chunk : chunks) {
            double dot = 0;
            for (Map.Entry<String, Double> entry : queryVector.entrySet()) {
                Double weight = chunk.weights().get(entry.getKey());
                if (weight != null) {
                    dot += entry.getValue() * weight;
                }
            }
            if (dot <= 0) {
                continue;
            }
            double score = dot / (queryNorm * chunk.norm());
            // 问题与文档标题高度重合时提权，避免正文里的同词干扰压过真正的手册
            if (titleOverlap(queryVector.keySet(), chunk.docId()) >= 2) {
                score *= 1.25;
            }
            if (score >= MIN_SCORE) {
                scored.add(chunk.withScore(score));
            }
        }
        scored.sort(Comparator.comparingDouble(Chunk::score).reversed());

        // 同一篇文档最多取两段，避免单个文档占满上下文
        Map<String, Integer> perDoc = new HashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        List<KnowledgeHit> hits = new ArrayList<>();
        for (Chunk chunk : scored) {
            int used = perDoc.getOrDefault(chunk.docId(), 0);
            if (used >= TOP_LIMIT_PER_DOC) {
                continue;
            }
            if (!seen.add(chunk.docId() + "#" + chunk.text().hashCode())) {
                continue;
            }
            perDoc.put(chunk.docId(), used + 1);
            hits.add(new KnowledgeHit(chunk.docId(), chunk.title(), chunk.text(),
                    Math.round(chunk.score() * 1000) / 1000.0));
            if (hits.size() >= topK) {
                break;
            }
        }
        return hits;
    }

    private void load() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources(LOCATION_PATTERN);
        } catch (IOException e) {
            log.warn("知识库目录读取失败，规则类问题将只能依赖提示词作答", e);
            return;
        }

        List<Chunk> raw = new ArrayList<>();
        for (Resource resource : resources) {
            try {
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                String fileName = resource.getFilename() == null ? "unknown" : resource.getFilename();
                String docId = fileName.replace(".md", "");
                String title = extractTitle(content, docId);
                titles.add(title);
                titleTokens.put(docId, new LinkedHashSet<>(tokenize(title)));
                for (String piece : splitIntoChunks(content)) {
                    raw.add(new Chunk(docId, title, piece, null, 0, 0));
                }
            } catch (IOException e) {
                log.warn("知识库文档读取失败：{}", resource.getFilename(), e);
            }
        }
        if (raw.isEmpty()) {
            log.warn("知识库为空，未找到 {} 下的文档", LOCATION_PATTERN);
            return;
        }

        // 第一遍统计文档频率，第二遍计算 tf-idf 与向量模长
        Map<String, Integer> docFreq = new HashMap<>();
        for (Chunk chunk : raw) {
            for (String token : new LinkedHashSet<>(tokenize(chunk.title() + chunk.text()))) {
                docFreq.merge(token, 1, Integer::sum);
            }
        }
        termDocFreq.putAll(docFreq);
        double total = raw.size();
        for (Map.Entry<String, Integer> entry : docFreq.entrySet()) {
            idf.put(entry.getKey(), Math.log((total + 1) / (entry.getValue() + 1)) + 1);
        }

        for (Chunk chunk : raw) {
            // 标题重复计入，提升与标题相关问题的命中率
            List<String> tokens = tokenize(chunk.title());
            tokens.addAll(tokenize(chunk.text()));
            tokens.addAll(tokenize(chunk.title()));
            Map<String, Double> weights = new HashMap<>();
            double norm = 0;
            for (Map.Entry<String, Integer> entry : termFrequency(tokens).entrySet()) {
                Double weight = idf.get(entry.getKey());
                if (weight == null) {
                    continue;
                }
                double value = (1 + Math.log(entry.getValue())) * weight * termBoost(entry.getKey());
                weights.put(entry.getKey(), value);
                norm += value * value;
            }
            if (norm > 0) {
                chunks.add(new Chunk(chunk.docId(), chunk.title(), chunk.text(), weights, Math.sqrt(norm), 0));
            }
        }
        log.info("知识库已加载：{} 篇文档，{} 个片段，{} 个词项", titles.size(), chunks.size(), idf.size());
    }

    private String extractTitle(String content, String fallback) {
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }
        return fallback;
    }

    /**
     * 以小节为单位切分片段：段落本身通常对应一条规则，切得越细命中越准。
     * 超长段落按句末标点再拆，避免单条规则被稀释。
     */
    private List<String> splitIntoChunks(String content) {
        List<String> chunks = new ArrayList<>();
        for (String paragraph : content.split("\n\\s*\n")) {
            String text = paragraph.trim();
            if (text.isEmpty() || text.startsWith("# ")) {
                continue;
            }
            text = text.replaceAll("^#+\\s*", "").trim();
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() <= CHUNK_MAX_CHARS) {
                if (text.length() >= CHUNK_MIN_CHARS) {
                    chunks.add(text);
                }
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String sentence : text.split("(?<=[。！？；])")) {
                if (current.length() > 0 && current.length() + sentence.length() > CHUNK_MAX_CHARS) {
                    if (current.length() >= CHUNK_MIN_CHARS) {
                        chunks.add(current.toString());
                    }
                    current.setLength(0);
                }
                current.append(sentence);
            }
            if (current.length() >= CHUNK_MIN_CHARS) {
                chunks.add(current.toString());
            }
        }
        return chunks;
    }

    /** 问题词项与文档标题的重合个数 */
    private int titleOverlap(java.util.Collection<String> queryTerms, String docId) {
        Set<String> tokens = titleTokens.get(docId);
        if (tokens == null || tokens.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String term : queryTerms) {
            if (tokens.contains(term)) {
                count++;
            }
        }
        return count;
    }

    private Map<String, Integer> termFrequency(List<String> tokens) {
        Map<String, Integer> tf = new HashMap<>();
        for (String token : tokens) {
            tf.merge(token, 1, Integer::sum);
        }
        return tf;
    }

    /**
     * 中文同时产出单字与二元组：二元组保证「退款」「限购」这类术语的精度，
     * 单字弥补提问用词与文档用词不一致时的召回，权重上压到二元组的四成。
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase());
        while (matcher.find()) {
            String piece = matcher.group();
            if (piece.charAt(0) < 128) {
                tokens.add(piece);
                continue;
            }
            for (int i = 0; i < piece.length(); i++) {
                tokens.add(String.valueOf(piece.charAt(i)));
                if (i < piece.length() - 1) {
                    tokens.add(piece.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }

    /** 单字权重低于二元组，避免常见字主导排序 */
    private double termBoost(String token) {
        return token.length() == 1 && token.charAt(0) > 127 ? 0.4 : 1.0;
    }

    private record Chunk(String docId, String title, String text,
                         Map<String, Double> weights, double norm, double score) {

        Chunk withScore(double value) {
            return new Chunk(docId, title, text, weights, norm, value);
        }
    }
}
