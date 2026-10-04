package com.journalflow.service;

import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.dto.EditorDtos.AppealCreateRequest;
import com.journalflow.dto.EditorDtos.AppealHandleRequest;
import com.journalflow.entity.Appeal;
import com.journalflow.entity.Comment;
import com.journalflow.entity.Manuscript;
import com.journalflow.entity.Report;
import com.journalflow.mapper.*;
import com.journalflow.statemachine.ManuscriptStateMachine;
import com.journalflow.statemachine.ManuscriptStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppealServiceSecurityTest {
    @Mock AppealMapper appealMapper;
    @Mock EditorialDecisionMapper decisionMapper;
    @Mock ReportMapper reportMapper;
    @Mock ManuscriptMapper manuscriptMapper;
    @Mock CommentMapper commentMapper;
    @Mock UserMapper userMapper;
    @Mock ManuscriptStateMachine stateMachine;
    @Mock NotificationService notificationService;
    @InjectMocks AppealService appealService;

    @Test
    void requiresExactlyOneAppealSource() {
        AppealCreateRequest req = request(1L, 2L);
        BizException error = assertThrows(BizException.class, () -> appealService.create(8L, req));
        assertEquals(ErrorCode.BAD_PARAM, error.getErrorCode());
    }

    @Test
    void rejectsAppealBeforeReportHasFinalResult() {
        Report report = report(2L, "PENDING", 8L, "COMMENT", 10L);
        when(reportMapper.selectById(2L)).thenReturn(report);
        BizException error = assertThrows(BizException.class,
                () -> appealService.create(8L, request(null, 2L)));
        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
    }

    @Test
    void onlyTargetOwnerCanAppealResolvedDisposition() {
        Report report = report(2L, "RESOLVED", 9L, "COMMENT", 10L);
        Comment comment = new Comment();
        comment.setId(10L);
        comment.setUserId(7L);
        when(reportMapper.selectById(2L)).thenReturn(report);
        when(commentMapper.selectById(10L)).thenReturn(comment);

        BizException error = assertThrows(BizException.class,
                () -> appealService.create(8L, request(null, 2L)));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void onlyOriginalReporterCanAppealDismissal() {
        Report report = report(2L, "DISMISSED", 9L, "USER", 7L);
        when(reportMapper.selectById(2L)).thenReturn(report);

        BizException error = assertThrows(BizException.class,
                () -> appealService.create(8L, request(null, 2L)));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void successfulAppealRestoresExactManuscriptWorkflowStatus() {
        Appeal appeal = new Appeal();
        appeal.setId(31L);
        appeal.setReportId(2L);
        appeal.setAppellantId(7L);
        appeal.setStatus("PENDING");
        Report report = report(2L, "RESOLVED", 8L, "MANUSCRIPT", 12L);
        report.setTargetStatusBefore("UNDER_REVIEW");
        Manuscript manuscript = new Manuscript();
        manuscript.setId(12L);
        manuscript.setStatus("WITHDRAWN");
        when(appealMapper.selectById(31L)).thenReturn(appeal);
        when(reportMapper.selectById(2L)).thenReturn(report);
        when(manuscriptMapper.selectById(12L)).thenReturn(manuscript);

        AppealHandleRequest request = new AppealHandleRequest();
        request.setStatus("RESOLVED");
        request.setResult("原举报证据不足，恢复稿件流程");
        appealService.handle(31L, 1L, request);

        verify(stateMachine).forceTransition(manuscript, ManuscriptStatus.UNDER_REVIEW,
                "举报申诉成立恢复原状态");
        verify(manuscriptMapper).updateById(manuscript);
    }

    @Test
    void legacyUnpublishedDispositionWithoutSnapshotIsNotGuessed() {
        Appeal appeal = new Appeal();
        appeal.setId(31L);
        appeal.setReportId(2L);
        appeal.setAppellantId(7L);
        appeal.setStatus("PENDING");
        Report report = report(2L, "RESOLVED", 8L, "MANUSCRIPT", 12L);
        Manuscript manuscript = new Manuscript();
        manuscript.setId(12L);
        manuscript.setStatus("WITHDRAWN");
        when(appealMapper.selectById(31L)).thenReturn(appeal);
        when(reportMapper.selectById(2L)).thenReturn(report);
        when(manuscriptMapper.selectById(12L)).thenReturn(manuscript);

        AppealHandleRequest request = new AppealHandleRequest();
        request.setStatus("RESOLVED");
        request.setResult("同意恢复");
        BizException error = assertThrows(BizException.class,
                () -> appealService.handle(31L, 1L, request));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
    }

    private AppealCreateRequest request(Long decisionId, Long reportId) {
        AppealCreateRequest req = new AppealCreateRequest();
        req.setDecisionId(decisionId);
        req.setReportId(reportId);
        req.setContent("申请复核处置结果");
        return req;
    }

    private Report report(Long id, String status, Long reporterId, String type, Long targetId) {
        Report report = new Report();
        report.setId(id);
        report.setStatus(status);
        report.setReporterId(reporterId);
        report.setTargetType(type);
        report.setTargetId(targetId);
        return report;
    }
}
