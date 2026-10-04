package com.journalflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.journalflow.common.BizException;
import com.journalflow.dto.ReviewDtos.DeclineRequest;
import com.journalflow.dto.ReviewDtos.ReportRequest;
import com.journalflow.entity.*;
import com.journalflow.mapper.*;
import com.journalflow.statemachine.ManuscriptStatus;
import com.journalflow.statemachine.ManuscriptStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 审稿专家侧：接受/拒绝邀请、查看任务、提交结构化审稿意见。
 */
@Service
@RequiredArgsConstructor
public class ReviewFlowService {
    private final ReviewAssignmentMapper assignmentMapper;
    private final ReviewReportMapper reportMapper;
    private final ManuscriptMapper manuscriptMapper;
    private final ManuscriptVersionMapper versionMapper;
    private final UserMapper userMapper;
    private final EditorialDecisionMapper decisionMapper;
    private final ManuscriptStateMachine stateMachine;
    private final NotificationService notificationService;

    public List<Map<String, Object>> myAssignments(Long reviewerId, String status) {
        return assignmentMapper.selectMyAssignments(null, reviewerId, status);
    }

    public Map<String, Object> assignmentDetail(Long assignmentId, Long reviewerId) {
        ReviewAssignment a = requireOwned(assignmentId, reviewerId);
        Manuscript m = manuscriptMapper.selectById(a.getManuscriptId());
        ManuscriptVersion v = versionMapper.selectById(a.getVersionId());
        ReviewReport myReport = reportMapper.selectOne(new LambdaQueryWrapper<ReviewReport>()
                .eq(ReviewReport::getAssignmentId, assignmentId));
        // 本人历史版本意见（辅助复审）
        List<ReviewReport> pastReports = reportMapper.selectList(new LambdaQueryWrapper<ReviewReport>()
                .eq(ReviewReport::getManuscriptId, a.getManuscriptId())
                .ne(ReviewReport::getVersionId, a.getVersionId())
                .inSql(ReviewReport::getAssignmentId,
                        "SELECT id FROM review_assignment WHERE reviewer_id = " + reviewerId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assignment", a);
        data.put("manuscript", m);
        data.put("version", v);
        data.put("myReport", myReport);
        data.put("pastReports", pastReports);
        data.put("editorName", userMapper.selectById(a.getInvitedBy()).getRealName());
        return data;
    }

    @Transactional
    public void accept(Long assignmentId, Long reviewerId) {
        ReviewAssignment a = requireOwned(assignmentId, reviewerId);
        if (!"PENDING".equals(a.getStatus())) {
            throw BizException.conflict("仅待接受的邀请可操作，当前：" + a.getStatus());
        }
        a.setStatus("ACCEPTED");
        assignmentMapper.updateById(a);
        Manuscript m = manuscriptMapper.selectById(a.getManuscriptId());
        notificationService.create(a.getInvitedBy(), "SYSTEM", "审稿邀请已被接受",
                "审稿人已接受《" + m.getTitle() + "》的审稿邀请。", a.getManuscriptId());
    }

    @Transactional
    public void decline(Long assignmentId, Long reviewerId, DeclineRequest req) {
        ReviewAssignment a = requireOwned(assignmentId, reviewerId);
        if (!"PENDING".equals(a.getStatus())) {
            throw BizException.conflict("仅待接受的邀请可操作，当前：" + a.getStatus());
        }
        a.setStatus("DECLINED");
        assignmentMapper.updateById(a);
        Manuscript m = manuscriptMapper.selectById(a.getManuscriptId());
        notificationService.create(a.getInvitedBy(), "SYSTEM", "审稿邀请被拒绝",
                "审稿人拒绝了《" + m.getTitle() + "》的审稿邀请。"
                        + (req.getReason() == null ? "" : "原因：" + req.getReason()), a.getManuscriptId());
        checkAutoDecision(a.getManuscriptId());
    }

    @Transactional
    public ReviewReport submitReport(Long assignmentId, Long reviewerId, ReportRequest req) {
        ReviewAssignment a = requireOwned(assignmentId, reviewerId);
        if (!"ACCEPTED".equals(a.getStatus())) {
            throw BizException.conflict("仅已接受的任务可提交意见，当前：" + a.getStatus());
        }
        Long exists = reportMapper.selectCount(new LambdaQueryWrapper<ReviewReport>()
                .eq(ReviewReport::getAssignmentId, assignmentId));
        if (exists > 0) {
            throw BizException.conflict("该任务已提交过审稿意见");
        }
        String rec = req.getRecommendation();
        if (!List.of("ACCEPT", "MINOR", "MAJOR", "REJECT").contains(rec)) {
            throw BizException.badParam("建议等级必须是 ACCEPT/MINOR/MAJOR/REJECT");
        }
        ReviewReport r = new ReviewReport();
        r.setAssignmentId(assignmentId);
        r.setManuscriptId(a.getManuscriptId());
        r.setVersionId(a.getVersionId());
        r.setScoreNovelty(req.getScoreNovelty());
        r.setScoreQuality(req.getScoreQuality());
        r.setScoreClarity(req.getScoreClarity());
        r.setCommentsToEditor(req.getCommentsToEditor());
        r.setCommentsToAuthor(req.getCommentsToAuthor());
        r.setRecommendation(rec);
        r.setSubmittedAt(LocalDateTime.now());
        reportMapper.insert(r);

        a.setStatus("COMPLETED");
        assignmentMapper.updateById(a);

        Manuscript m = manuscriptMapper.selectById(a.getManuscriptId());
        notificationService.create(a.getInvitedBy(), "SYSTEM", "收到新的审稿意见",
                "《" + m.getTitle() + "》（v" + versionNoOf(a.getVersionId()) + "）有一条新的审稿意见。", a.getManuscriptId());
        checkAutoDecision(a.getManuscriptId());
        return r;
    }

    /** 全部审稿任务完成且至少一份意见 → 审稿中自动流转到待编辑决定 */
    private void checkAutoDecision(Long manuscriptId) {
        Manuscript m = manuscriptMapper.selectById(manuscriptId);
        if (m == null || !ManuscriptStatus.UNDER_REVIEW.name().equals(m.getStatus())
                || m.getCurrentVersionId() == null) {
            return;
        }
        List<ReviewAssignment> current = assignmentMapper.selectList(new LambdaQueryWrapper<ReviewAssignment>()
                .eq(ReviewAssignment::getManuscriptId, manuscriptId)
                .eq(ReviewAssignment::getVersionId, m.getCurrentVersionId()));
        boolean anyActive = current.stream().anyMatch(a -> "PENDING".equals(a.getStatus()) || "ACCEPTED".equals(a.getStatus()));
        boolean anyCompleted = current.stream().anyMatch(a -> "COMPLETED".equals(a.getStatus()));
        if (!anyActive && anyCompleted) {
            stateMachine.transition(m, ManuscriptStatus.PENDING_DECISION);
            manuscriptMapper.updateById(m);
            current.stream().map(ReviewAssignment::getInvitedBy).distinct()
                    .forEach(editorId -> notificationService.create(editorId, "SYSTEM",
                            "稿件《" + m.getTitle() + "》审稿意见已齐",
                            "所有审稿任务已结束，请汇总意见并作出决定。", manuscriptId));
        }
    }

    private ReviewAssignment requireOwned(Long assignmentId, Long reviewerId) {
        ReviewAssignment a = assignmentMapper.selectById(assignmentId);
        if (a == null) throw BizException.notFound("审稿任务不存在");
        if (!a.getReviewerId().equals(reviewerId)) {
            throw BizException.forbidden("只能操作分配给自己的审稿任务");
        }
        return a;
    }

    private Integer versionNoOf(Long versionId) {
        ManuscriptVersion v = versionMapper.selectById(versionId);
        return v == null ? null : v.getVersionNo();
    }
}
