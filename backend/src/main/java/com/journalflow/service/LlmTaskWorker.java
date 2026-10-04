package com.journalflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 在创建任务的数据库事务提交后异步调用模型。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmTaskWorker {
    private final LlmService llmService;

    @Async("llmTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void execute(LlmTaskCreatedEvent event) {
        try {
            llmService.executeTask(event.taskId(), event.systemPrompt(), event.userPrompt());
        } catch (Exception e) {
            log.error("后台 AI 任务 {} 出现未处理异常", event.taskId(), e);
            llmService.markUnexpectedFailure(event.taskId(), e.getMessage());
        }
    }
}
