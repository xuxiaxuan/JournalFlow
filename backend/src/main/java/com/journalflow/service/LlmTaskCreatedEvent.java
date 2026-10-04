package com.journalflow.service;

/** 事务提交后交给后台线程执行的 AI 任务输入。 */
public record LlmTaskCreatedEvent(Long taskId, String systemPrompt, String userPrompt) {
}
