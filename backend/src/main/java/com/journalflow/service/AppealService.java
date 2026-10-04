package com.journalflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.journalflow.common.BizException;
import com.journalflow.common.PageVO;
import com.journalflow.dto.EditorDtos.AppealCreateRequest;
import com.journalflow.dto.EditorDtos.AppealHandleRequest;
import com.journalflow.entity.*;
import com.journalflow.mapper.AppealMapper;
import com.journalflow.mapper.CommentMapper;
import com.journalflow.mapper.EditorialDecisionMapper;
import com.journalflow.mapper.ManuscriptMapper;
import com.journalflow.mapper.ReportMapper;
import com.journalflow.mapper.UserMapper;
import com.journalflow.statemachine.ManuscriptStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 申诉：退稿申诉（1 次，管理员终审可翻案）与举报处置申诉。
 */
@Service
@RequiredArgsConstructor
public class AppealService {
    private final AppealMapper appealMapper;
    private final EditorialDecisionMapper decisionMapper;
    private final ReportMapper reportMapper;
    private final ManuscriptMapper manuscriptMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final com.journalflow.statemachine.ManuscriptStateMachine stateMachine;
    private final NotificationService notificationService;

    @Transactional
    public Appeal create(Long uid, AppealCreateRequest req) {
        if ((req.getDecisionId() == null) == (req.getReportId() == null)) {
            throw BizException.badParam("退稿决定 ID 与举报工单 ID 必须且只能提供一个");
        }
        if (req.getDecisionId() != null) {
            EditorialDecision d = decisionMapper.selectById(req.getDecisionId());
            if (d == null) throw BizException.notFound("决定不存在");
            if (!"REVIEW".equals(d.getStage()) || !"REJECT".equals(d.getDecision())) {
                throw BizException.badParam("只能对退稿决定提出申诉");
            }
            Manuscript m = manuscriptMapper.selectById(d.getManuscriptId());
            if (m == null || !m.getSubmitterId().equals(uid)) {
                throw BizException.forbidden("只能对本人稿件的决定申诉");
            }
            Long exists = appealMapper.selectCount(new LambdaQueryWrapper<Appeal>()
                    .eq(Appeal::getDecisionId, req.getDecisionId()));
            if (exists > 0) {
                throw BizException.conflict("该决定已提交过申诉（仅 1 次机会）");
            }
        } else {
            Report r = reportMapper.selectById(req.getReportId());
            if (r == null) throw BizException.notFound("举报工单不存在");
            if (!List.of("RESOLVED", "DISMISSED").contains(r.getStatus())) {
                throw BizException.conflict("举报尚未形成最终处置结果，不能申诉");
            }
            if ("RESOLVED".equals(r.getStatus())) {
                Long targetOwnerId = reportTargetOwner(r);
                if (targetOwnerId == null || !targetOwnerId.equals(uid)) {
                    throw BizException.forbidden("只有被处置内容或账号的所有者可以提出申诉");
                }
            } else if (!r.getReporterId().equals(uid)) {
                throw BizException.forbidden("只有原举报人可以对驳回结果提出申诉");
            }
            Long exists = appealMapper.selectCount(new LambdaQueryWrapper<Appeal>()
                    .eq(Appeal::getReportId, req.getReportId()));
            if (exists > 0) {
                throw BizException.conflict("该处置已提交过申诉");
            }
        }
        Appeal a = new Appeal();
        a.setReportId(req.getReportId());
        a.setDecisionId(req.getDecisionId());
        a.setAppellantId(uid);
        a.setContent(req.getContent());
        a.setStatus("PENDING");
        a.setCreatedAt(LocalDateTime.now());
        appealMapper.insert(a);
        // 通知管理员终审
        for (User admin : userMapper.selectList(new LambdaQueryWrapper<User>()
                .inSql(User::getId, "SELECT user_id FROM user_role WHERE role_id = (SELECT id FROM role WHERE code = 'ADMIN')"))) {
            notificationService.create(admin.getId(), "APPEAL", "收到新的申诉，请终审",
                    "申诉内容：" + abbreviate(req.getContent(), 60), a.getId());
        }
        return a;
    }

    private Long reportTargetOwner(Report report) {
        return switch (report.getTargetType()) {
            case "MANUSCRIPT" -> {
                Manuscript manuscript = manuscriptMapper.selectById(report.getTargetId());
                yield manuscript == null ? null : manuscript.getSubmitterId();
            }
            case "COMMENT" -> {
                Comment comment = commentMapper.selectById(report.getTargetId());
                yield comment == null ? null : comment.getUserId();
            }
            case "USER" -> report.getTargetId();
            default -> null;
        };
    }

    public PageVO<Map<String, Object>> page(Long appellantId, String status, long page, long size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        List<Map<String, Object>> rows = appealMapper.selectPage(p, appellantId, status);
        return com.journalflow.common.PageVO.of(rows, p.getTotal(), page, size);
    }

    @Transactional
    public void handle(Long id, Long adminId, AppealHandleRequest req) {
        Appeal a = appealMapper.selectById(id);
        if (a == null) throw BizException.notFound("申诉不存在");
        if (!"PENDING".equals(a.getStatus())) {
            throw BizException.conflict("该申诉已裁决");
        }
        if (!List.of("RESOLVED", "DISMISSED").contains(req.getStatus())) {
            throw BizException.badParam("裁决结果必须是 RESOLVED（成立）/ DISMISSED（驳回）");
        }
        a.setStatus(req.getStatus());
        a.setResult(req.getResult());
        a.setHandledBy(adminId);
        a.setHandledAt(LocalDateTime.now());
        appealMapper.updateById(a);

        if ("RESOLVED".equals(req.getStatus())) {
            applyOverturn(a, adminId, req.getResult());
        }
        String label = "RESOLVED".equals(req.getStatus()) ? "申诉成立" : "申诉被驳回";
        notificationService.create(a.getAppellantId(), "APPEAL", "您的申诉有终审结果：" + label,
                "申诉 #" + a.getId() + (req.getResult() == null ? "" : "：" + req.getResult()), a.getId());
    }

    /** 申诉成立：撤销原决定/处置（留痕 stage=APPEAL decision=OVERTURN） */
    private void applyOverturn(Appeal a, Long adminId, String result) {
        if (a.getDecisionId() != null) {
            EditorialDecision d = decisionMapper.selectById(a.getDecisionId());
            if (d == null) return;
            Manuscript m = manuscriptMapper.selectById(d.getManuscriptId());
            if (m != null && ManuscriptStatus.REJECTED.name().equals(m.getStatus())) {
                EditorialDecision overturn = new EditorialDecision();
                overturn.setManuscriptId(m.getId());
                overturn.setVersionId(m.getCurrentVersionId());
                overturn.setStage("APPEAL");
                overturn.setDecision("OVERTURN");
                overturn.setReason("退稿申诉成立：" + (result == null ? "" : result));
                overturn.setDecidedBy(adminId);
                overturn.setDecidedAt(LocalDateTime.now());
                decisionMapper.insert(overturn);
                stateMachine.forceTransition(m, ManuscriptStatus.PENDING_DECISION, "申诉翻案");
                manuscriptMapper.updateById(m);
                // 通知编辑重新决策
                notificationService.create(m.getSubmitterId(), "APPEAL", "稿件《" + m.getTitle() + "》退稿申诉成立",
                        "稿件已回到待编辑决定状态，等待编辑部复议。", m.getId());
            }
        } else if (a.getReportId() != null) {
            Report r = reportMapper.selectById(a.getReportId());
            if (r == null) return;
            switch (r.getTargetType()) {
                case "COMMENT" -> {
                    Comment c = commentMapper.selectById(r.getTargetId());
                    if (c != null && c.getStatus() == 0) {
                        c.setStatus(restoredNumericStatus(r, 1));
                        commentMapper.updateById(c);
                    }
                }
                case "MANUSCRIPT" -> {
                    Manuscript m = manuscriptMapper.selectById(r.getTargetId());
                    if (m != null && ManuscriptStatus.WITHDRAWN.name().equals(m.getStatus())) {
                        ManuscriptStatus restored = restoredManuscriptStatus(r, m);
                        stateMachine.forceTransition(m, restored, "举报申诉成立恢复原状态");
                        manuscriptMapper.updateById(m);
                    }
                }
                case "USER" -> {
                    User u = userMapper.selectById(r.getTargetId());
                    if (u != null && u.getStatus() == 0) {
                        u.setStatus(restoredNumericStatus(r, 1));
                        userMapper.updateById(u);
                    }
                }
                default -> { }
            }
        }
    }

    private ManuscriptStatus restoredManuscriptStatus(Report report, Manuscript manuscript) {
        if (report.getTargetStatusBefore() != null && !report.getTargetStatusBefore().isBlank()) {
            ManuscriptStatus status = ManuscriptStatus.of(report.getTargetStatusBefore());
            if (status != ManuscriptStatus.WITHDRAWN) return status;
        }
        // 兼容升级前的历史处置：仅能从出版时间可靠判断原稿确已公开。
        if (manuscript.getPublishedAt() != null) return ManuscriptStatus.PUBLISHED;
        throw BizException.conflict("历史举报缺少原状态快照，无法自动恢复，请人工核对");
    }

    private int restoredNumericStatus(Report report, int legacyDefault) {
        try {
            return report.getTargetStatusBefore() == null
                    ? legacyDefault : Integer.parseInt(report.getTargetStatusBefore());
        } catch (NumberFormatException ignored) {
            return legacyDefault;
        }
    }

    private String abbreviate(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
