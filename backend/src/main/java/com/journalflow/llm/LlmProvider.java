package com.journalflow.llm;

/**
 * 大模型 Provider 统一抽象：OpenAI 兼容 /chat/completions。
 * 场景参数仅供 Mock 实现返回预置输出，真实实现可忽略。
 */
public interface LlmProvider {

    record ChatResult(String content, int latencyMs) {}

    ChatResult chat(String scene, String systemPrompt, String userPrompt);

    /** 供 llm_task.provider 字段记录 */
    String name();

    /** 供 llm_task.model 字段记录 */
    String model();
}
