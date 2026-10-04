package com.journalflow.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.config.JournalProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Provider：适配智谱 GLM / DeepSeek / 通义 / Ollama 等的 /chat/completions。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatProvider implements LlmProvider {
    private final JournalProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpClient client() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ChatResult chat(String scene, String systemPrompt, String userPrompt) {
        JournalProperties.Llm cfg = props.getLlm();
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = Map.of(
                    "model", cfg.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)),
                    "temperature", cfg.getTemperature(),
                    "max_tokens", cfg.getMaxTokens(),
                    "response_format", Map.of("type", "json_object"));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getBaseUrl().replaceAll("/$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(cfg.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;
            if (response.statusCode() / 100 != 2) {
                throw new BizException(ErrorCode.DEP_FAILED,
                        "大模型接口返回 " + response.statusCode() + "：" + abbreviate(response.body(), 200));
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new BizException(ErrorCode.DEP_FAILED, "大模型返回内容为空");
            }
            return new ChatResult(content, (int) latency);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.DEP_FAILED, "大模型调用失败：" + abbreviate(e.getMessage(), 200));
        }
    }

    @Override
    public String name() {
        return "openai-compat";
    }

    @Override
    public String model() {
        return props.getLlm().getModel();
    }

    private String abbreviate(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
