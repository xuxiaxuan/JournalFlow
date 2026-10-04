package com.journalflow.llm;

import com.journalflow.config.JournalProperties;
import org.springframework.stereotype.Component;

/**
 * Provider 路由：根据 journal.llm.provider 配置选择 mock 或 openai 兼容实现。
 */
@Component
public class LlmProviderRouter {
    private final MockLlmProvider mockProvider;
    private final OpenAiCompatProvider openAiCompatProvider;
    private final JournalProperties props;

    public LlmProviderRouter(MockLlmProvider mockProvider,
                             OpenAiCompatProvider openAiCompatProvider,
                             JournalProperties props) {
        this.mockProvider = mockProvider;
        this.openAiCompatProvider = openAiCompatProvider;
        this.props = props;
    }

    public LlmProvider current() {
        return "openai".equalsIgnoreCase(props.getLlm().getProvider())
                || "openai-compat".equalsIgnoreCase(props.getLlm().getProvider())
                ? openAiCompatProvider : mockProvider;
    }
}
