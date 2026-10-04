package com.journalflow.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 代表性样例批跑：每个样例独立出现在 Surefire 报告中，检查字段完整性、
 * 枚举合法性以及标题/语言/伦理声明等可直接核对的事实一致性。
 */
class LlmRepresentativeSamplesTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final MockLlmProvider provider = new MockLlmProvider();

    record Sample(String name, String title, String abstractText, String keywords,
                  String body, String references, String language, boolean ethics) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void extractedMetadataIsCompleteAndFactuallyConsistent(Sample sample) throws Exception {
        String input = "【标题】" + sample.title()
                + "\n【摘要】" + sample.abstractText()
                + "\n【关键词】" + sample.keywords()
                + "\n【正文】" + sample.body()
                + "\n【参考文献】\n" + sample.references();

        JsonNode result = JSON.readTree(provider.chat("EXTRACT", "", input).content());

        assertTrue(result.isObject(), "输出必须是 JSON 对象");
        assertEquals(sample.title(), result.path("title").asText(), "标题不得被编造或改写");
        assertEquals(sample.language(), result.path("language").asText(), "语言识别应与样例一致");
        assertEquals(sample.ethics(), result.path("has_ethics_statement").asBoolean(), "伦理声明判断应与原文一致");
        assertFalse(result.path("abstract").isMissingNode(), "摘要字段必须存在");
        assertTrue(result.path("keywords").isArray(), "关键词必须是数组");
        assertTrue(result.path("keywords").size() >= 3 && result.path("keywords").size() <= 8,
                "关键词数量应在 3-8 个之间");
        assertTrue(result.path("primary_category").isTextual(), "学科分类必须存在");
        assertTrue(java.util.Set.of("人工智能", "软件工程", "网络与信息安全", "数学", "物理学", "生物学", "经济学")
                .contains(result.path("primary_category").asText()), "学科分类必须属于约定枚举");
        assertTrue(result.path("word_count_est").isIntegralNumber(), "字数估计必须为整数");
        assertTrue(result.path("reference_count_est").isIntegralNumber(), "参考文献计数必须为整数");
        long expectedReferences = sample.references().lines().filter(line -> !line.isBlank()).count();
        assertEquals(expectedReferences, result.path("reference_count_est").asLong(), "参考文献数量应与输入一致");
        assertTrue(result.path("possible_issues").isArray(), "问题列表必须是数组");
    }

    static Stream<Arguments> samples() {
        return Stream.of(
                sample("正常中文稿", "大模型辅助的稿件审查研究", "研究大模型在期刊审查中的应用。", "大模型；学术审查；人在回路", "本文提出结构化抽取方法。", refs(3), "zh", false),
                sample("英文稿", "Large Language Models for Peer Review", "This study evaluates language models in academic peer review.", "language model;peer review;human oversight", "The experiments compare three baselines and discuss limitations.", refs(4), "en", false),
                sample("缺摘要稿", "软件测试流程自动化方法", "", "软件工程；自动化测试；质量保障", "本文分析持续集成环境中的测试流程。", refs(2), "zh", false),
                sample("含伦理声明稿", "医疗文本分类中的隐私保护", "研究使用完全匿名的模拟病例。", "医疗文本；隐私保护；文本分类", "本研究已通过伦理审查并取得知情同意。", refs(5), "zh", true),
                sample("中文长文本稿", "超长学术文档的信息抽取", "面向长文档设计分段抽取与合并方法。", "长文档；信息抽取；上下文窗口", "长文本".repeat(1200), refs(10), "zh", false),
                sample("人工智能分类稿", "神经网络模型压缩方法", "提出面向边缘设备的模型压缩方案。", "神经网络；模型压缩；边缘计算", "机器学习模型采用量化和剪枝。", refs(6), "zh", false),
                sample("软件工程分类稿", "微服务架构质量评估", "构建微服务架构的质量指标体系。", "微服务；软件架构；质量评估", "通过软件工程案例验证方法。", refs(3), "zh", false),
                sample("信息安全分类稿", "联邦学习隐私泄露防御", "分析联邦学习中的梯度泄露风险。", "联邦学习；隐私计算；网络安全", "提出差分隐私与安全聚合方案。", refs(8), "zh", false),
                sample("经济学分类稿", "数字金融对消费行为的影响", "研究数字金融服务与居民消费的关系。", "数字金融；消费行为；实证研究", "使用市场调查数据进行回归分析。", refs(7), "zh", false),
                sample("术语密集稿", "检索增强生成系统的可信评测", "评测RAG系统的事实一致性。", "检索增强生成；事实一致性；可信评测", "包含向量检索、重排序、生成归因和幻觉检测。", refs(9), "zh", false),
                sample("双语摘要稿", "跨语言科学文本对齐方法", "提出跨语言对齐方法。Cross-lingual alignment is evaluated.", "跨语言；文本对齐；科学文献", "中文主体内容用于保证主语言识别正确。", refs(4), "zh", false),
                sample("无参考文献稿", "教学场景中的智能问答原型", "实现面向教学的智能问答系统。", "智能问答；教学辅助；原型系统", "系统采用检索和生成两阶段架构。", "", "zh", false),
                sample("多关键词稿", "知识图谱增强推荐研究", "分析知识图谱对推荐效果的影响。", "知识图谱；推荐系统；图神经网络；表示学习；对比学习；可解释性；冷启动；信息检索", "实验覆盖多个公开数据集。", refs(5), "zh", false),
                sample("第二个伦理样例", "访谈数据的主题分析", "使用脱敏访谈文本开展主题分析。", "访谈研究；主题分析；数据脱敏", "参与者均签署知情同意书，研究遵守伦理规范。", refs(3), "zh", true)
        );
    }

    private static Arguments sample(String name, String title, String abstractText, String keywords,
                                    String body, String references, String language, boolean ethics) {
        return Arguments.of(new Sample(name, title, abstractText, keywords, body, references, language, ethics));
    }

    private static String refs(int count) {
        StringBuilder value = new StringBuilder();
        for (int i = 1; i <= count; i++) value.append('[').append(i).append("] 虚构参考文献\n");
        return value.toString();
    }
}
