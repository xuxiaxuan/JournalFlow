package com.journalflow.service;

import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.dto.EditorDtos.ReportCreateRequest;
import com.journalflow.dto.EditorDtos.ReportHandleRequest;
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
class ReportServiceSecurityTest {
    @Mock ReportMapper reportMapper;
    @Mock CommentMapper commentMapper;
    @Mock ManuscriptMapper manuscriptMapper;
    @Mock UserMapper userMapper;
    @Mock EditorialDecisionMapper decisionMapper;
    @Mock ManuscriptStateMachine stateMachine;
    @Mock NotificationService notificationService;
    @InjectMocks ReportService reportService;

    @Test
    void unsupportedReasonTypeIsRejected() {
        ReportCreateRequest request = createRequest("COMMENT", 10L, "UNKNOWN");

        BizException error = assertThrows(BizException.class,
                () -> reportService.create(8L, request));

        assertEquals(ErrorCode.BAD_PARAM, error.getErrorCode());
    }

    @Test
    void nonexistentUserCannotBecomeReportTarget() {
        ReportCreateRequest request = createRequest("USER", 999L, "ABUSE");
        when(userMapper.selectById(999L)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> reportService.create(8L, request));

        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
    }

    @Test
    void hiddenCommentCannotBeReportedAgain() {
        Comment comment = new Comment();
        comment.setId(10L);
        comment.setUserId(7L);
        comment.setStatus(0);
        when(commentMapper.selectById(10L)).thenReturn(comment);

        BizException error = assertThrows(BizException.class,
                () -> reportService.create(8L, createRequest("COMMENT", 10L, "ABUSE")));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
    }

    @Test
    void manuscriptDispositionSnapshotsOriginalWorkflowStatus() {
        Report report = new Report();
        report.setId(21L);
        report.setReporterId(8L);
        report.setTargetType("MANUSCRIPT");
        report.setTargetId(12L);
        report.setReasonType("PLAGIARISM");
        report.setStatus("PENDING");
        Manuscript manuscript = new Manuscript();
        manuscript.setId(12L);
        manuscript.setSubmitterId(7L);
        manuscript.setCurrentVersionId(30L);
        manuscript.setTitle("待审稿件");
        manuscript.setStatus("UNDER_REVIEW");
        when(reportMapper.selectById(21L)).thenReturn(report);
        when(manuscriptMapper.selectById(12L)).thenReturn(manuscript);

        ReportHandleRequest request = new ReportHandleRequest();
        request.setStatus("RESOLVED");
        request.setResolution("核实存在严重学术不端");
        reportService.handle(21L, 301L, false, request);

        assertEquals("UNDER_REVIEW", report.getTargetStatusBefore());
        verify(stateMachine).forceTransition(manuscript, ManuscriptStatus.WITHDRAWN, "举报处置下架");
        verify(reportMapper).updateById(report);
    }

    private ReportCreateRequest createRequest(String type, Long targetId, String reason) {
        ReportCreateRequest request = new ReportCreateRequest();
        request.setTargetType(type);
        request.setTargetId(targetId);
        request.setReasonType(reason);
        request.setDescription("举报事实说明");
        return request;
    }
}
