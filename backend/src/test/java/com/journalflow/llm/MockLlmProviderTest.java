package com.journalflow.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockLlmProviderTest {

    private final MockLlmProvider provider = new MockLlmProvider();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractsTraceableStructuredMetadata() throws Exception {
        String input = "【标题】大模型辅助的学术审稿系统\n"
                + "【摘要】本文研究自然语言处理在学术审稿中的应用，并包含伦理声明。\n"
                + "【关键词】大模型；自然语言处理；学术审稿\n"
                + "【正文】本文提出一个人在回路的软件工程方案。\n"
                + "【参考文献】\n[1] 示例文献\n[2] 另一篇文献";

        LlmProvider.ChatResult result = provider.chat("EXTRACT", "", input);
        JsonNode json = objectMapper.readTree(result.content());

        assertEquals("大模型辅助的学术审稿系统", json.path("title").asText());
        assertEquals("zh", json.path("language").asText());
        assertTrue(json.path("has_ethics_statement").asBoolean());
        assertTrue(json.path("keywords").isArray());
        assertTrue(json.path("keywords").size() >= 3);
        assertTrue(json.path("reference_count_est").asInt() >= 2);
        assertEquals("mock", provider.name());
        assertFalse(provider.model().isBlank());
    }

    @Test
    void allSupportedScenesReturnValidJson() throws Exception {
        assertValidJson(provider.chat("SUMMARY", "",
                "意见1：建议等级：MINOR；给作者：请补充实验。\n意见2：建议等级：MINOR；给作者：请完善讨论。").content());
        assertValidJson(provider.chat("EXPLAIN", "", "1. 请补充实验。\n2. 请完善讨论。").content());
    }

    private void assertValidJson(String value) throws Exception {
        JsonNode json = objectMapper.readTree(value);
        assertTrue(json.isObject());
    }

    @Test
    void countsReferencesInRealExtractedBodyWithoutSyntheticMarker() throws Exception {
        String input = "【标题】学术研究\n【摘要】论文摘要\n【关键词】研究;学术;实验\n【正文】真实正文\nReferences\n[1] First paper\n[2] Second paper";
        JsonNode result = objectMapper.readTree(provider.chat("EXTRACT", "", input).content());
        assertEquals(2, result.path("reference_count_est").asInt());
    }
}
