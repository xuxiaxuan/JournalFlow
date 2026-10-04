package com.journalflow.service;

import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.dto.ManuscriptDtos.CopyrightRequest;
import com.journalflow.dto.ManuscriptDtos.PublishRequest;
import com.journalflow.dto.EditorDtos.DecisionRequest;
import com.journalflow.entity.CopyrightRecord;
import com.journalflow.entity.EditorialDecision;
import com.journalflow.entity.Manuscript;
import com.journalflow.entity.LlmTask;
import com.journalflow.mapper.*;
import com.journalflow.statemachine.ManuscriptStateMachine;
import com.journalflow.statemachine.ManuscriptStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EditorFlowServiceSecurityTest {
    @Mock ManuscriptMapper manuscriptMapper;
    @Mock ManuscriptVersionMapper versionMapper;
    @Mock CategoryMapper categoryMapper;
    @Mock UserMapper userMapper;
    @Mock ReviewAssignmentMapper assignmentMapper;
    @Mock EditorialDecisionMapper decisionMapper;
    @Mock CopyrightRecordMapper copyrightMapper;
    @Mock LlmTaskMapper llmTaskMapper;
    @Mock ManuscriptStateMachine stateMachine;
    @Mock NotificationService notificationService;
    @InjectMocks EditorFlowService editorFlowService;

    @Test
    void copyrightMustBeSignedBySubmitter() {
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript("ACCEPTED"));

        BizException error = assertThrows(BizException.class,
                () -> editorFlowService.signCopyright(8L, 999L, copyrightRequest()));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void copyrightCannotBeSignedBeforeAcceptance() {
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript("UNDER_REVIEW"));

        BizException error = assertThrows(BizException.class,
                () -> editorFlowService.signCopyright(8L, 101L, copyrightRequest()));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
    }

    @Test
    void currentVersionCannotBeSignedTwice() {
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript("ACCEPTED"));
        when(copyrightMapper.selectCount(any())).thenReturn(1L);

        BizException error = assertThrows(BizException.class,
                () -> editorFlowService.signCopyright(8L, 101L, copyrightRequest()));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
    }

    @Test
    void successfulSignatureKeepsVersionSignerAndAgreementHash() {
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript("ACCEPTED"));
        when(copyrightMapper.selectCount(any())).thenReturn(0L);

        CopyrightRecord result = editorFlowService.signCopyright(8L, 101L, copyrightRequest());

        ArgumentCaptor<CopyrightRecord> captor = ArgumentCaptor.forClass(CopyrightRecord.class);
        verify(copyrightMapper).insert(captor.capture());
        CopyrightRecord stored = captor.getValue();
        assertSame(stored, result);
        assertEquals(8L, stored.getManuscriptId());
        assertEquals(10L, stored.getVersionId());
        assertEquals(101L, stored.getSignedBy());
        assertEquals("CC-BY-4.0", stored.getLicenseType());
        assertEquals("本人同意版权许可协议", stored.getAgreementText());
        assertNotNull(stored.getSignedAt());
        assertNotNull(stored.getAgreementHash());
        assertEquals(64, stored.getAgreementHash().length());
    }

    @Test
    void unsignedVersionCannotEnterProduction() {
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript("ACCEPTED"));
        when(copyrightMapper.selectCount(any())).thenReturn(0L);

        BizException error = assertThrows(BizException.class,
                () -> editorFlowService.publish(8L, 301L, publishRequest()));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
    }

    @Test
    void signedVersionCanEnterProductionWithPublicationMetadata() {
        Manuscript manuscript = manuscript("ACCEPTED");
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript);
        when(copyrightMapper.selectCount(any())).thenReturn(1L);

        editorFlowService.publish(8L, 301L, publishRequest());

        verify(stateMachine).transition(manuscript, ManuscriptStatus.IN_PRODUCTION);
        verify(manuscriptMapper).updateById(manuscript);
        assertEquals("12", manuscript.getVolume());
        assertEquals("3", manuscript.getIssue());
        assertEquals("1-12", manuscript.getPages());
        assertEquals("10.1234/jf.2026.8", manuscript.getDoi());
    }

    @Test
    void duplicateDoiCannotEnterProduction() {
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript("ACCEPTED"));
        when(copyrightMapper.selectCount(any())).thenReturn(1L);
        when(manuscriptMapper.selectCount(any())).thenReturn(1L);

        BizException error = assertThrows(BizException.class,
                () -> editorFlowService.publish(8L, 301L, publishRequest()));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        assertEquals("DOI 已被其他稿件使用", error.getMessage());
    }

    @Test
    void incompleteMetadataCannotBeArchived() {
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript("IN_PRODUCTION"));

        BizException error = assertThrows(BizException.class,
                () -> editorFlowService.archive(8L, 301L));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        assertEquals("出版元数据或归档版本不完整，不能正式发布", error.getMessage());
    }

    @Test
    void archiveKeepsVersionBoundPublicationTraceAndNotifiesSubscribers() {
        Manuscript manuscript = manuscript("IN_PRODUCTION");
        manuscript.setVolume("12");
        manuscript.setIssue("3");
        manuscript.setPages("1-12");
        manuscript.setDoi("10.1234/jf.2026.8");
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript);
        when(copyrightMapper.selectCount(any())).thenReturn(1L);
        when(manuscriptMapper.selectSubscriberUserIds(8L)).thenReturn(List.of(101L, 202L));

        editorFlowService.archive(8L, 301L);

        verify(stateMachine).transition(manuscript, ManuscriptStatus.PUBLISHED);
        verify(manuscriptMapper).updateById(manuscript);
        assertNotNull(manuscript.getPublishedAt());

        ArgumentCaptor<EditorialDecision> decisionCaptor = ArgumentCaptor.forClass(EditorialDecision.class);
        verify(decisionMapper).insert(decisionCaptor.capture());
        EditorialDecision trace = decisionCaptor.getValue();
        assertEquals(8L, trace.getManuscriptId());
        assertEquals(10L, trace.getVersionId());
        assertEquals("PUBLICATION", trace.getStage());
        assertEquals("PUBLISHED", trace.getDecision());
        assertEquals(301L, trace.getDecidedBy());
        assertTrue(trace.getReason().contains("10.1234/jf.2026.8"));

        verify(notificationService).create(101L, "PUBLISH", "稿件《大模型辅助审稿研究》已正式发布",
                "文章已归档发布，可在公开检索页查看。", 8L);
        verify(notificationService).create(202L, "SUBSCRIPTION", "您订阅的内容有新文章发布",
                "《大模型辅助审稿研究》已正式出版。", 8L);
    }

    @Test
    void editorialDecisionRejectsUnconfirmedAiSummary() {
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript("PENDING_DECISION"));
        LlmTask task = summaryTask("SUCCESS");
        when(llmTaskMapper.selectById(15L)).thenReturn(task);

        BizException error = assertThrows(BizException.class,
                () -> editorFlowService.decide(8L, 301L, decisionRequest(15L)));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
    }

    @Test
    void confirmedAiSummaryIsLinkedAndUsedAsDecisionSnapshot() {
        Manuscript manuscript = manuscript("PENDING_DECISION");
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript);
        when(llmTaskMapper.selectById(15L)).thenReturn(summaryTask("CONFIRMED"));
        DecisionRequest request = decisionRequest(15L);

        editorFlowService.decide(8L, 301L, request);

        assertEquals("{\"summary\":\"人工确认汇总\"}", request.getSummaryReport());
        verify(stateMachine).transition(manuscript, ManuscriptStatus.ACCEPTED);
        verify(decisionMapper).insert(any());
    }

    private Manuscript manuscript(String status) {
        Manuscript manuscript = new Manuscript();
        manuscript.setId(8L);
        manuscript.setSubmitterId(101L);
        manuscript.setCurrentVersionId(10L);
        manuscript.setTitle("大模型辅助审稿研究");
        manuscript.setStatus(status);
        return manuscript;
    }

    private CopyrightRequest copyrightRequest() {
        CopyrightRequest request = new CopyrightRequest();
        request.setLicenseType("CC-BY-4.0");
        request.setAgreementText("本人同意版权许可协议");
        return request;
    }

    private LlmTask summaryTask(String status) {
        LlmTask task = new LlmTask();
        task.setVersionId(10L);
        task.setId(15L);
        task.setManuscriptId(8L);
        task.setScene("SUMMARY");
        task.setStatus(status);
        task.setConfirmedOutput("{\"summary\":\"人工确认汇总\"}");
        return task;
    }

    private DecisionRequest decisionRequest(Long taskId) {
        DecisionRequest request = new DecisionRequest();
        request.setDecision("ACCEPT");
        request.setLlmTaskId(taskId);
        return request;
    }

    @Test
    void editorialDecisionRejectsHistoricalSummary() {
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript("PENDING_DECISION"));
        LlmTask task = summaryTask("CONFIRMED"); task.setVersionId(9L);
        when(llmTaskMapper.selectById(15L)).thenReturn(task);
        assertEquals(ErrorCode.CONFLICT, assertThrows(BizException.class,
                () -> editorFlowService.decide(8L, 301L, decisionRequest(15L))).getErrorCode());
    }

    private PublishRequest publishRequest() {
        PublishRequest request = new PublishRequest();
        request.setVolume("12");
        request.setIssue("3");
        request.setPages("1-12");
        request.setDoi("10.1234/jf.2026.8");
        return request;
    }
}
