package com.journalflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.journalflow.common.BizException;
import com.journalflow.common.PageVO;
import com.journalflow.dto.EditorDtos.DecisionRequest;
import com.journalflow.dto.EditorDtos.FormatReviewRequest;
import com.journalflow.dto.ManuscriptDtos.CopyrightRequest;
import com.journalflow.dto.ManuscriptDtos.PublishRequest;
import com.journalflow.dto.ReviewDtos.AssignReviewersRequest;
import com.journalflow.entity.*;
import com.journalflow.mapper.*;
import com.journalflow.statemachine.ManuscriptStatus;
import com.journalflow.statemachine.ManuscriptStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 编辑工作台：形式审查、送审、决定、出版归档、版权协议。
 */
@Service
@RequiredArgsConstructor
public class EditorFlowService {
    private final ManuscriptMapper manuscriptMapper;
    private final ManuscriptVersionMapper versionMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final ReviewAssignmentMapper assignmentMapper;
    private final EditorialDecisionMapper decisionMapper;
    private final CopyrightRecordMapper copyrightMapper;
    private final LlmTaskMapper llmTaskMapper;
    private final ManuscriptStateMachine stateMachine;
    private final NotificationService notificationService;

    // ---------------- 形式审查 ----------------

    public PageVO<Map<String, Object>> formatQueue(long page, long size, String status, String keyword) {
        Page<Manuscript> p = new Page<>(page, size);
        LambdaQueryWrapper<Manuscript> w = new LambdaQueryWrapper<Manuscript>()
                .in(Manuscript::getStatus,
                        status != null && !status.isBlank()
                                ? List.of(status)
                                : List.of(ManuscriptStatus.SUBMITTED.name(), ManuscriptStatus.IN_FORMAT_REVIEW.name()))
                .like(keyword != null && !keyword.isBlank(), Manuscript::getTitle, keyword)
                .orderByDesc(Manuscript::getUpdatedAt);
        Page<Manuscript> result = manuscriptMapper.selectPage(p, w);
        Map<Long, String> categoryNames = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        List<Map<String, Object>> rows = result.getRecords().stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("title", m.getTitle());
            row.put("abstract", m.getAbstract_());
            row.put("keywords", m.getKeywords());
            row.put("status", m.getStatus());
            row.put("categoryName", categoryNames.get(m.getCategoryId()));
            row.put("submitterName", userMapper.selectById(m.getSubmitterId()).getRealName());
            row.put("createdAt", m.getCreatedAt());
            ManuscriptVersion v = m.getCurrentVersionId() == null ? null : versionMapper.selectById(m.getCurrentVersionId());
            row.put("versionId", v == null ? null : v.getId());
            row.put("versionNo", v == null ? null : v.getVersionNo());
            row.put("fileName", v == null ? null : v.getFileName());
            return row;
        }).toList();
        return PageVO.of(rows, result.getTotal(), page, size);
    }

    @Transactional
    public void startFormatReview(Long id, Long editorId) {
        Manuscript m = requireManuscript(id);
        stateMachine.transition(m, ManuscriptStatus.IN_FORMAT_REVIEW);
        manuscriptMapper.updateById(m);
    }

    @Transactional
    public void completeFormatReview(Long id, Long editorId, FormatReviewRequest req) {
        Manuscript m = requireManuscript(id);
        ManuscriptStatus target = Boolean.TRUE.equals(req.getPass()) ? ManuscriptStatus.UNDER_REVIEW : ManuscriptStatus.REJECTED;
        stateMachine.transition(m, target);
        manuscriptMapper.updateById(m);

        EditorialDecision d = new EditorialDecision();
        d.setManuscriptId(id);
        d.setVersionId(m.getCurrentVersionId());
        d.setStage("FORMAT");
        d.setDecision(Boolean.TRUE.equals(req.getPass()) ? "PASS" : "FAIL");
        d.setReason(req.getReason());
        d.setDecidedBy(editorId);
        d.setDecidedAt(LocalDateTime.now());
        decisionMapper.insert(d);

        if (target == ManuscriptStatus.UNDER_REVIEW) {
            notificationService.create(m.getSubmitterId(), "DECISION", "稿件《" + m.getTitle() + "》形式审查通过",
                    "稿件已进入专家审稿阶段。", id);
        } else {
            notificationService.create(m.getSubmitterId(), "DECISION", "稿件《" + m.getTitle() + "》未通过形式审查",
                    req.getReason() == null ? "稿件未通过形式审查，被退稿。" : req.getReason(), id);
        }
    }

    // ---------------- 送审 ----------------

    @Transactional
    public List<ReviewAssignment> assignReviewers(Long id, Long editorId, AssignReviewersRequest req) {
        Manuscript m = requireManuscript(id);
        if (!ManuscriptStatus.UNDER_REVIEW.name().equals(m.getStatus())) {
            throw BizException.conflict("仅「审稿中」状态可分配审稿人，当前：" + m.getStatus());
        }
        if (m.getCurrentVersionId() == null) {
            throw BizException.conflict("稿件暂无版本，无法送审");
        }
        List<ReviewAssignment> created = new ArrayList<>();
        for (Long reviewerId : req.getReviewerIds()) {
            User reviewer = userMapper.selectById(reviewerId);
            if (reviewer == null || !userMapper.selectRoleCodes(reviewerId).contains("REVIEWER")) {
                throw BizException.badParam("用户 " + reviewerId + " 不是审稿专家");
            }
            ReviewAssignment a = new ReviewAssignment();
            a.setManuscriptId(id);
            a.setVersionId(m.getCurrentVersionId());
            a.setReviewerId(reviewerId);
            a.setInvitedBy(editorId);
            a.setInvitedAt(LocalDateTime.now());
            a.setDueAt(req.getDueAt());
            a.setStatus("PENDING");
            try {
                assignmentMapper.insert(a);
            } catch (Exception e) {
                throw BizException.conflict("审稿人 " + reviewer.getRealName() + " 在当前版本已有审稿任务");
            }
            notificationService.create(reviewerId, "REVIEW_INVITE", "您有新的审稿邀请：" + m.getTitle(),
                    "邀请人：编辑。请在截止日期前接受或拒绝邀请。", id);
            created.add(a);
        }
        return created;
    }

    @Transactional
    public void cancelAssignment(Long assignmentId, Long editorId) {
        ReviewAssignment a = assignmentMapper.selectById(assignmentId);
        if (a == null) throw BizException.notFound("审稿任务不存在");
        if (!"PENDING".equals(a.getStatus())) {
            throw BizException.conflict("仅「待接受」的任务可取消");
        }
        assignmentMapper.deleteById(assignmentId);
        Manuscript m = manuscriptMapper.selectById(a.getManuscriptId());
        notificationService.create(a.getReviewerId(), "SYSTEM", "审稿邀请已取消",
                "稿件《" + (m == null ? "" : m.getTitle()) + "》的审稿邀请已被编辑取消。", a.getManuscriptId());
    }

    // ---------------- 编辑决定 ----------------

    @Transactional
    public void decide(Long id, Long editorId, DecisionRequest req) {
        Manuscript m = requireManuscript(id);
        if (!ManuscriptStatus.PENDING_DECISION.name().equals(m.getStatus())) {
            throw BizException.conflict("仅「待编辑决定」状态可作出决定，当前：" + m.getStatus());
        }
        if (req.getLlmTaskId() != null) {
            LlmTask task = llmTaskMapper.selectById(req.getLlmTaskId());
            if (task == null || !id.equals(task.getManuscriptId()) || !"SUMMARY".equals(task.getScene())) {
                throw BizException.badParam("只能关联当前稿件的审稿意见汇总任务");
            }
            if (!"CONFIRMED".equals(task.getStatus())) {
                throw BizException.conflict("AI 汇总任务必须人工确认后才能关联编辑决定");
            }
            if (!Objects.equals(m.getCurrentVersionId(), task.getVersionId())) {
                throw BizException.conflict("AI 汇总属于历史版本，请使用当前版本的汇总结果");
            }
            if (req.getSummaryReport() == null || req.getSummaryReport().isBlank()) {
                req.setSummaryReport(task.getConfirmedOutput());
            }
        }
        ManuscriptStatus target = switch (req.getDecision()) {
            case "ACCEPT" -> ManuscriptStatus.ACCEPTED;
            case "REVISE" -> ManuscriptStatus.REVISION_REQUIRED;
            case "REJECT" -> ManuscriptStatus.REJECTED;
            default -> throw BizException.badParam("决定必须是 ACCEPT/REVISE/REJECT");
        };
        stateMachine.transition(m, target);
        manuscriptMapper.updateById(m);

        EditorialDecision d = new EditorialDecision();
        d.setManuscriptId(id);
        d.setVersionId(m.getCurrentVersionId());
        d.setStage("REVIEW");
        d.setDecision(req.getDecision());
        d.setReason(req.getReason());
        d.setSummaryReport(req.getSummaryReport());
        d.setLlmTaskId(req.getLlmTaskId());
        d.setDecidedBy(editorId);
        d.setDecidedAt(LocalDateTime.now());
        decisionMapper.insert(d);

        String label = ManuscriptService.decisionLabel(req.getDecision());
        notificationService.create(m.getSubmitterId(), "DECISION",
                "稿件《" + m.getTitle() + "》审稿决定：" + label,
                switch (req.getDecision()) {
                    case "ACCEPT" -> "恭喜！稿件被录用，请等待编辑部安排版权签署与出版。";
                    case "REVISE" -> "稿件需要修改，请查看审稿意见并在系统中修回。";
                    default -> "很遗憾，稿件被退稿。如对决定有异议，可在系统中提交申诉（仅 1 次）。";
                }, id);
    }

    // ---------------- 出版归档 ----------------

    @Transactional
    public void publish(Long id, Long editorId, PublishRequest req) {
        Manuscript m = requireManuscript(id);
        if (m.getCurrentVersionId() == null) {
            throw BizException.conflict("稿件暂无可出版版本");
        }
        Long signed = copyrightMapper.selectCount(new LambdaQueryWrapper<CopyrightRecord>()
                .eq(CopyrightRecord::getManuscriptId, id)
                .eq(CopyrightRecord::getVersionId, m.getCurrentVersionId()));
        if (signed == 0) {
            throw BizException.conflict("作者尚未签署当前版本的版权协议，不能进入出版加工");
        }
        String doi = req.getDoi().trim();
        Long duplicateDoi = manuscriptMapper.selectCount(new LambdaQueryWrapper<Manuscript>()
                .eq(Manuscript::getDoi, doi)
                .ne(Manuscript::getId, id));
        if (duplicateDoi > 0) {
            throw BizException.conflict("DOI 已被其他稿件使用");
        }
        stateMachine.transition(m, ManuscriptStatus.IN_PRODUCTION);
        m.setVolume(req.getVolume().trim());
        m.setIssue(req.getIssue().trim());
        m.setPages(req.getPages().trim());
        m.setDoi(doi);
        manuscriptMapper.updateById(m);
    }

    @Transactional
    public void archive(Long id, Long editorId) {
        Manuscript m = requireManuscript(id);
        if (m.getCurrentVersionId() == null || isBlank(m.getVolume()) || isBlank(m.getIssue())
                || isBlank(m.getPages()) || isBlank(m.getDoi())) {
            throw BizException.conflict("出版元数据或归档版本不完整，不能正式发布");
        }
        Long signed = copyrightMapper.selectCount(new LambdaQueryWrapper<CopyrightRecord>()
                .eq(CopyrightRecord::getManuscriptId, id)
                .eq(CopyrightRecord::getVersionId, m.getCurrentVersionId()));
        if (signed == 0) {
            throw BizException.conflict("当前归档版本缺少版权签署记录，不能正式发布");
        }
        stateMachine.transition(m, ManuscriptStatus.PUBLISHED);
        LocalDateTime publishedAt = LocalDateTime.now();
        m.setPublishedAt(publishedAt);
        manuscriptMapper.updateById(m);

        EditorialDecision publication = new EditorialDecision();
        publication.setManuscriptId(id);
        publication.setVersionId(m.getCurrentVersionId());
        publication.setStage("PUBLICATION");
        publication.setDecision("PUBLISHED");
        publication.setReason("正式出版归档：" + m.getVolume() + "卷 " + m.getIssue()
                + "期，页码 " + m.getPages() + "，DOI " + m.getDoi());
        publication.setDecidedBy(editorId);
        publication.setDecidedAt(publishedAt);
        decisionMapper.insert(publication);

        notificationService.create(m.getSubmitterId(), "PUBLISH", "稿件《" + m.getTitle() + "》已正式发布",
                "文章已归档发布，可在公开检索页查看。", id);
        // 订阅推送：匹配学科/关键词的用户收到站内消息
        for (Long subscriberId : manuscriptMapper.selectSubscriberUserIds(id)) {
            if (!subscriberId.equals(m.getSubmitterId())) {
                notificationService.create(subscriberId, "SUBSCRIPTION", "您订阅的内容有新文章发布",
                        "《" + m.getTitle() + "》已正式出版。", id);
            }
        }
    }

    // ---------------- 版权协议 ----------------

    @Transactional
    public CopyrightRecord signCopyright(Long id, Long signerId, CopyrightRequest req) {
        Manuscript m = requireManuscript(id);
        if (!m.getSubmitterId().equals(signerId)) {
            throw BizException.forbidden("版权协议必须由稿件作者本人签署");
        }
        if (!ManuscriptStatus.ACCEPTED.name().equals(m.getStatus())
                && !ManuscriptStatus.IN_PRODUCTION.name().equals(m.getStatus())
                && !ManuscriptStatus.PUBLISHED.name().equals(m.getStatus())) {
            throw BizException.conflict("仅录用后的稿件可签署版权协议");
        }
        Long exists = copyrightMapper.selectCount(new LambdaQueryWrapper<CopyrightRecord>()
                .eq(CopyrightRecord::getManuscriptId, id)
                .eq(CopyrightRecord::getVersionId, m.getCurrentVersionId()));
        if (exists > 0) {
            throw BizException.conflict("当前稿件版本已签署版权协议");
        }
        CopyrightRecord c = new CopyrightRecord();
        c.setManuscriptId(id);
        c.setVersionId(m.getCurrentVersionId());
        c.setLicenseType(req.getLicenseType() == null ? "CC-BY-4.0" : req.getLicenseType());
        c.setAgreementText(req.getAgreementText());
        c.setAgreementHash(sha256(req.getAgreementText()));
        c.setSignedBy(signerId);
        c.setSignedAt(LocalDateTime.now());
        copyrightMapper.insert(c);
        notificationService.create(m.getSubmitterId(), "SYSTEM", "版权协议已签署留痕",
                "稿件《" + m.getTitle() + "》的版权协议（" + c.getLicenseType() + "）已完成签署存证。", id);
        return c;
    }

    private Manuscript requireManuscript(Long id) {
        Manuscript m = manuscriptMapper.selectById(id);
        if (m == null) throw BizException.notFound("稿件不存在");
        return m;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
