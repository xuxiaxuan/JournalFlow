package com.journalflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.journalflow.common.BizException;
import com.journalflow.common.PageVO;
import com.journalflow.dto.EditorDtos.ReportCreateRequest;
import com.journalflow.dto.EditorDtos.ReportHandleRequest;
import com.journalflow.entity.Comment;
import com.journalflow.entity.EditorialDecision;
import com.journalflow.entity.Manuscript;
import com.journalflow.entity.Report;
import com.journalflow.entity.User;
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
 * 举报工单与处置流：处置动作（评论隐藏/稿件下架/账号禁用）留痕并可申诉。
 */
@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportMapper reportMapper;
    private final CommentMapper commentMapper;
    private final ManuscriptMapper manuscriptMapper;
    private final UserMapper userMapper;
    private final EditorialDecisionMapper decisionMapper;
    private final com.journalflow.statemachine.ManuscriptStateMachine stateMachine;
    private final NotificationService notificationService;

    @Transactional
    public Report create(Long uid, ReportCreateRequest req) {
        if (!List.of("MANUSCRIPT", "COMMENT", "USER").contains(req.getTargetType())) {
            throw BizException.badParam("举报对象类型非法");
        }
        if (!List.of("CONTENT", "COPYRIGHT", "PLAGIARISM", "ABUSE", "OTHER")
                .contains(req.getReasonType())) {
            throw BizException.badParam("举报原因类型非法");
        }
        Long targetOwnerId = switch (req.getTargetType()) {
            case "MANUSCRIPT" -> {
                Manuscript m = manuscriptMapper.selectById(req.getTargetId());
                if (m != null && ManuscriptStatus.WITHDRAWN.name().equals(m.getStatus())) {
                    throw BizException.conflict("该稿件已撤回或下架，无需重复举报");
                }
                yield m == null ? null : m.getSubmitterId();
            }
            case "COMMENT" -> {
                Comment c = commentMapper.selectById(req.getTargetId());
                if (c != null && !Integer.valueOf(1).equals(c.getStatus())) {
                    throw BizException.conflict("该评论已被隐藏或删除，无需重复举报");
                }
                yield c == null ? null : c.getUserId();
            }
            default -> {
                User u = userMapper.selectById(req.getTargetId());
                if (u != null && !Integer.valueOf(1).equals(u.getStatus())) {
                    throw BizException.conflict("该账号已被禁用，无需重复举报");
                }
                yield u == null ? null : u.getId();
            }
        };
        if (targetOwnerId == null) {
            throw BizException.notFound("举报对象不存在");
        }
        if (targetOwnerId.equals(uid)) {
            throw BizException.badParam("不能举报自己的内容/账号");
        }
        Report r = new Report();
        r.setReporterId(uid);
        r.setTargetType(req.getTargetType());
        r.setTargetId(req.getTargetId());
        r.setReasonType(req.getReasonType());
        r.setDescription(req.getDescription());
        r.setStatus("PENDING");
        r.setCreatedAt(LocalDateTime.now());
        reportMapper.insert(r);
        return r;
    }

    public PageVO<Map<String, Object>> page(Long reporterId, String status, String targetType, long page, long size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        List<Map<String, Object>> rows = reportMapper.selectPage(p, reporterId, status, targetType);
        return PageVO.of(rows, p.getTotal(), page, size);
    }

    @Transactional
    public void handle(Long id, Long handlerId, boolean isAdmin, ReportHandleRequest req) {
        Report r = reportMapper.selectById(id);
        if (r == null) throw BizException.notFound("举报工单不存在");
        if (!"PENDING".equals(r.getStatus()) && !"PROCESSING".equals(r.getStatus())) {
            throw BizException.conflict("该工单已处理");
        }
        if (!List.of("RESOLVED", "DISMISSED").contains(req.getStatus())) {
            throw BizException.badParam("处理结果必须是 RESOLVED/DISMISSED");
        }
        if ("USER".equals(r.getTargetType()) && !isAdmin) {
            throw BizException.forbidden("涉及账号处置的举报仅管理员可处理");
        }
        if ("RESOLVED".equals(req.getStatus())) {
            applyAction(r, handlerId);
        }
        r.setStatus(req.getStatus());
        r.setHandledBy(handlerId);
        r.setHandledAt(LocalDateTime.now());
        r.setResolution(req.getResolution());
        reportMapper.updateById(r);

        Long targetOwner = targetOwner(r);
        String resultLabel = "RESOLVED".equals(req.getStatus()) ? "举报已处置" : "举报已驳回";
        notificationService.create(r.getReporterId(), "REPORT", "您的举报有处理进展：" + resultLabel,
                "工单 #" + r.getId() + (req.getResolution() == null ? "" : "：" + req.getResolution()), r.getId());
        if (targetOwner != null && "RESOLVED".equals(req.getStatus())) {
            notificationService.create(targetOwner, "REPORT", "您的内容/账号被处置",
                    "经编辑部核实，您的" + targetLabel(r.getTargetType()) + "被处置"
                            + (req.getResolution() == null ? "" : "：" + req.getResolution())
                            + "。如不服处置，可在申诉模块提出申诉。", r.getId());
        }
    }

    /** 处置动作：评论隐藏 / 稿件下架 / 账号禁用 */
    private void applyAction(Report r, Long handlerId) {
        switch (r.getTargetType()) {
            case "COMMENT" -> {
                Comment c = commentMapper.selectById(r.getTargetId());
                if (c != null) {
                    r.setTargetStatusBefore(String.valueOf(c.getStatus()));
                    c.setStatus(0);
                    commentMapper.updateById(c);
                }
            }
            case "MANUSCRIPT" -> {
                Manuscript m = manuscriptMapper.selectById(r.getTargetId());
                if (m != null && !ManuscriptStatus.WITHDRAWN.name().equals(m.getStatus())) {
                    r.setTargetStatusBefore(m.getStatus());
                    // 治理下架：特殊流转并留痕
                    EditorialDecision d = new EditorialDecision();
                    d.setManuscriptId(m.getId());
                    d.setVersionId(m.getCurrentVersionId());
                    d.setStage("GOVERNANCE");
                    d.setDecision("RETRACT");
                    d.setReason("举报处置下架：" + r.getReasonType());
                    d.setDecidedBy(handlerId);
                    d.setDecidedAt(LocalDateTime.now());
                    decisionMapper.insert(d);
                    stateMachine.forceTransition(m, ManuscriptStatus.WITHDRAWN, "举报处置下架");
                    manuscriptMapper.updateById(m);
                    notificationService.create(m.getSubmitterId(), "REPORT", "稿件《" + m.getTitle() + "》被下架",
                            "经举报核实，稿件已被下架处理。如不服处置，可在申诉模块提出申诉。", m.getId());
                }
            }
            case "USER" -> {
                User u = userMapper.selectById(r.getTargetId());
                if (u != null) {
                    r.setTargetStatusBefore(String.valueOf(u.getStatus()));
                    u.setStatus(0);
                    userMapper.updateById(u);
                    notificationService.create(u.getId(), "REPORT", "您的账号被禁用",
                            "经举报核实，您的账号已被禁用。如不服处置，可在申诉模块提出申诉。", r.getId());
                }
            }
            default -> { }
        }
    }

    private Long targetOwner(Report r) {
        return switch (r.getTargetType()) {
            case "MANUSCRIPT" -> {
                Manuscript m = manuscriptMapper.selectById(r.getTargetId());
                yield m == null ? null : m.getSubmitterId();
            }
            case "COMMENT" -> {
                Comment c = commentMapper.selectById(r.getTargetId());
                yield c == null ? null : c.getUserId();
            }
            default -> r.getTargetId();
        };
    }

    private String targetLabel(String type) {
        return switch (type) {
            case "MANUSCRIPT" -> "稿件";
            case "COMMENT" -> "评论";
            default -> "账号";
        };
    }
}
