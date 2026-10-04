package com.journalflow.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.config.JournalProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP transport against a local fixture, NOT an external-model evaluation. */
class OpenAiCompatProviderTest {
    private HttpServer server;
    private OpenAiCompatProvider provider;
    private int status = 200;
    private String response = "{\"choices\":[{\"message\":{\"content\":\"{\\\"title\\\":\\\"模拟标题\\\"}\"}}]}";
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });
        server.start();
        JournalProperties props = new JournalProperties();
        props.getLlm().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        props.getLlm().setApiKey("local-fixture-not-a-real-key");
        props.getLlm().setModel("fixture-model");
        props.getLlm().setTimeoutSeconds(2);
        provider = new OpenAiCompatProvider(props);
    }

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    @Test
    void sendsStructuredChinesePromptAndReadsContent() throws Exception {
        var result = provider.chat("EXTRACT", "输出 JSON，不补造事实。", "模拟稿件正文");
        assertEquals("{\"title\":\"模拟标题\"}", result.content());
        assertTrue(result.latencyMs() >= 0);
        assertEquals("Bearer local-fixture-not-a-real-key", authorization.get());
        JsonNode request = new ObjectMapper().readTree(requestBody.get());
        assertEquals("fixture-model", request.path("model").asText());
        assertEquals("system", request.path("messages").get(0).path("role").asText());
        assertEquals("模拟稿件正文", request.path("messages").get(1).path("content").asText());
        assertEquals("json_object", request.path("response_format").path("type").asText());
    }

    @Test
    void reportsRateLimitAsDependencyFailure() {
        status = 429;
        response = "{\"error\":\"fixture rate limit\"}";
        BizException error = assertThrows(BizException.class, () -> provider.chat("EXTRACT", "system", "user"));
        assertEquals(ErrorCode.DEP_FAILED, error.getErrorCode());
        assertTrue(error.getMessage().contains("429"));
    }

    @Test
    void rejectsEmptyModelContent() {
        response = "{\"choices\":[]}";
        BizException error = assertThrows(BizException.class, () -> provider.chat("EXTRACT", "system", "user"));
        assertEquals(ErrorCode.DEP_FAILED, error.getErrorCode());
        assertTrue(error.getMessage().contains("为空"));
    }

    @Test
    void rejectsMalformedResponseEnvelope() {
        response = "not JSON";
        BizException error = assertThrows(BizException.class, () -> provider.chat("EXTRACT", "system", "user"));
        assertEquals(ErrorCode.DEP_FAILED, error.getErrorCode());
    }
}
