package com.journalflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.journalflow.entity.LlmTask;
import com.journalflow.mapper.LlmTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/** 重启时清理上一个进程遗留的运行中任务，避免页面永久显示“运行中”。 */
@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "journal.llm.recovery-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class LlmTaskRecovery {
    private final LlmTaskMapper llmTaskMapper;
    private final LlmService llmService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        List<LlmTask> interrupted = llmTaskMapper.selectList(new LambdaQueryWrapper<LlmTask>()
                .eq(LlmTask::getStatus, "RUNNING"));
        if (!interrupted.isEmpty()) {
            log.warn("发现 {} 个因上次服务中断而遗留的 AI 任务，将标记为失败", interrupted.size());
        }
        for (LlmTask task : interrupted) {
            llmService.markUnexpectedFailure(task.getId(), "服务重启导致后台任务中断，请重新发起");
        }
    }
}
