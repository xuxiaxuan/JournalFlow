package com.journalflow.service;

import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.entity.LlmTask;
import com.journalflow.entity.Manuscript;
import com.journalflow.entity.ManuscriptVersion;
import com.journalflow.entity.MetadataRevision;
import com.journalflow.llm.LlmProviderRouter;
import com.journalflow.llm.LlmProvider;
import com.journalflow.mapper.*;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class LlmServiceTraceTest {
    @Mock LlmTaskMapper llmTaskMapper;
    @Mock ManuscriptMapper manuscriptMapper;
    @Mock ManuscriptVersionMapper versionMapper;
    @Mock ReviewReportMapper reportMapper;
    @Mock CategoryMapper categoryMapper;
    @Mock UserMapper userMapper;
    @Mock MetadataRevisionMapper metadataRevisionMapper;
    @Mock LlmProviderRouter providerRouter;
    @Mock NotificationService notificationService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ManuscriptTextExtractor textExtractor;
    @Mock LlmProvider provider;
    @InjectMocks LlmService llmService;

    @Test
    void duplicateActiveTaskForSameVersionAndSceneIsRejected() {
        Manuscript manuscript = manuscript();
        ManuscriptVersion version = new ManuscriptVersion();
        version.setId(10L);
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript);
        when(versionMapper.selectById(10L)).thenReturn(version);
        when(llmTaskMapper.selectCount(any())).thenReturn(1L);

        BizException error = assertThrows(BizException.class,
                () -> llmService.createTask("EXTRACT", 8L, 301L));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
    }

    @Test
    void confirmedExtractionStoresBeforeAndAfterMetadataTrace() {
        LlmTask task = new LlmTask();
        task.setId(15L);
        task.setScene("EXTRACT");
        task.setStatus("SUCCESS");
        task.setManuscriptId(8L);
        task.setVersionId(10L);
        Manuscript manuscript = manuscript();
        manuscript.setTitle("原始标题");
        manuscript.setAbstract_("原始摘要");
        manuscript.setKeywords("旧关键词");
        manuscript.setCategoryId(2L);
        when(llmTaskMapper.selectById(15L)).thenReturn(task);
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript);
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        String confirmed = """
                {"title":"人工确认标题","abstract":"人工确认摘要","keywords":["大模型","审稿","追溯"],
                 "primary_category":"人工智能","language":"zh","word_count_est":5000,
                 "reference_count_est":20,"has_ethics_statement":false,"possible_issues":[]}
                """;
        llmService.confirm(15L, 301L, confirmed);

        assertEquals("人工确认标题", manuscript.getTitle());
        verify(manuscriptMapper).updateById(manuscript);
        ArgumentCaptor<MetadataRevision> captor = ArgumentCaptor.forClass(MetadataRevision.class);
        verify(metadataRevisionMapper).insert(captor.capture());
        MetadataRevision trace = captor.getValue();
        assertEquals(8L, trace.getManuscriptId());
        assertEquals(10L, trace.getVersionId());
        assertEquals(15L, trace.getLlmTaskId());
        assertEquals(301L, trace.getChangedBy());
        assertTrue(trace.getBeforeData().contains("原始标题"));
        assertTrue(trace.getAfterData().contains("人工确认标题"));
        assertNotNull(trace.getCreatedAt());
    }

    @Test
    void onlyFailedTaskCanBeRetried() {
        LlmTask task = new LlmTask();
        task.setId(15L);
        task.setStatus("SUCCESS");
        when(llmTaskMapper.selectById(15L)).thenReturn(task);

        BizException error = assertThrows(BizException.class,
                () -> llmService.retry(15L, 301L));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
    }

    @Test
    void taskCreationReturnsRunningAndPublishesBackgroundEvent() throws Exception {
        Manuscript manuscript = manuscript();
        ManuscriptVersion version = new ManuscriptVersion();
        version.setId(10L);
        version.setVersionNo(1);
        version.setTitle("测试稿件");
        version.setAbstract_("测试摘要");
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript);
        when(versionMapper.selectById(10L)).thenReturn(version);
        when(llmTaskMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.selectList(null)).thenReturn(java.util.List.of());
        when(providerRouter.current()).thenReturn(provider);
        when(provider.name()).thenReturn("mock");
        when(provider.model()).thenReturn("mock-journal-assistant-v1");
        when(textExtractor.extract(version)).thenReturn("真实论文正文与参考文献内容");

        LlmTask task = llmService.createTask("EXTRACT", 8L, 301L);

        assertEquals("RUNNING", task.getStatus());
        assertEquals("mock", task.getProvider());
        verify(llmTaskMapper).insert(task);
        ArgumentCaptor<LlmTaskCreatedEvent> event = ArgumentCaptor.forClass(LlmTaskCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        org.junit.jupiter.api.Assertions.assertTrue(event.getValue().userPrompt().contains("真实论文正文与参考文献内容"));
        org.junit.jupiter.api.Assertions.assertFalse(event.getValue().userPrompt().contains("演示参考文献标题"));
    }

    @Test
    void backgroundExecutionStoresValidatedResultAndNotifiesCreator() throws Exception {
        LlmTask task = new LlmTask();
        task.setId(15L);
        task.setScene("EXPLAIN");
        task.setStatus("RUNNING");
        task.setManuscriptId(8L);
        task.setCreatedBy(101L);
        when(llmTaskMapper.selectById(15L)).thenReturn(task);
        when(providerRouter.current()).thenReturn(provider);
        when(provider.chat("EXPLAIN", "system", "input")).thenReturn(new LlmProvider.ChatResult(
                "{\"explanations\":[{\"issue\":\"补实验\",\"plain\":\"证据不足\",\"suggestion\":\"增加对比实验\"}],\"action_items\":[\"增加实验\"]}", 25));

        llmService.executeTask(15L, "system", "input");

        assertEquals("SUCCESS", task.getStatus());
        assertEquals(25, task.getLatencyMs());
        assertNotNull(task.getParsedOutput());
        verify(llmTaskMapper).updateById(task);
        verify(notificationService).create(101L, "LLM", "AI 任务执行完成",
                "意见解释任务 #15 已完成，请查看结果。", 15L);
    }

    @Test
    void staleAndPublishedExtractionCannotOverwriteMetadata() {
        LlmTask task = new LlmTask();
        task.setId(77L); task.setScene("EXTRACT"); task.setStatus("SUCCESS");
        task.setVersionId(9L); task.setManuscriptId(8L);
        when(llmTaskMapper.selectById(77L)).thenReturn(task);
        Manuscript m = manuscript();
        when(manuscriptMapper.selectById(8L)).thenReturn(m);
        String output = """
                {"title":"旧标题","abstract":"摘要","keywords":["甲","乙","丙"],"primary_category":"人工智能",
                "language":"zh","word_count_est":10,"reference_count_est":0,"has_ethics_statement":false,"possible_issues":[]}
                """;
        assertEquals(ErrorCode.CONFLICT, assertThrows(BizException.class, () -> llmService.confirm(77L, 2L, output)).getErrorCode());
        task.setVersionId(10L); m.setStatus("PUBLISHED");
        assertEquals(ErrorCode.CONFLICT, assertThrows(BizException.class, () -> llmService.confirm(77L, 2L, output)).getErrorCode());
        org.mockito.Mockito.verify(manuscriptMapper, org.mockito.Mockito.never()).updateById(any(Manuscript.class));
        assertEquals("SUCCESS", task.getStatus());
    }

    @Test
    void malformedOutputIsPreservedOnFailure() {
        LlmTask task = new LlmTask(); task.setId(88L); task.setStatus("RUNNING"); task.setScene("EXPLAIN");
        when(llmTaskMapper.selectById(88L)).thenReturn(task);
        when(providerRouter.current()).thenReturn(provider);
        when(provider.chat("EXPLAIN", "system", "input")).thenReturn(new LlmProvider.ChatResult("not JSON", 5));
        llmService.executeTask(88L, "system", "input");
        assertEquals("FAILED", task.getStatus());
        assertEquals("not JSON", task.getRawOutput());
    }

    private Manuscript manuscript() {
        Manuscript manuscript = new Manuscript();
        manuscript.setId(8L);
        manuscript.setStatus("IN_FORMAT_REVIEW");
        manuscript.setCurrentVersionId(10L);
        return manuscript;
    }
}
