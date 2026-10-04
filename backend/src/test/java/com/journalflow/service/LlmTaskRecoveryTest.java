package com.journalflow.service;

import com.journalflow.entity.LlmTask;
import com.journalflow.mapper.LlmTaskMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmTaskRecoveryTest {
    @Test
    void marksInterruptedRunningTasksAsFailedOnStartup() {
        LlmTaskMapper mapper = mock(LlmTaskMapper.class);
        LlmService service = mock(LlmService.class);
        LlmTask task = new LlmTask();
        task.setId(42L);
        when(mapper.selectList(any())).thenReturn(List.of(task));

        new LlmTaskRecovery(mapper, service).recoverInterruptedTasks();

        verify(service).markUnexpectedFailure(42L, "服务重启导致后台任务中断，请重新发起");
    }
}
