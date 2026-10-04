package com.journalflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.journalflow.common.BizException;
import com.journalflow.common.PageVO;
import com.journalflow.common.SecurityUtils;
import com.journalflow.dto.ManuscriptDtos;
import com.journalflow.dto.ManuscriptDtos.SaveRequest;
import com.journalflow.entity.*;
import com.journalflow.mapper.*;
import com.journalflow.security.UserDetailsImpl;
import com.journalflow.statemachine.ManuscriptStatus;
import com.journalflow.statemachine.ManuscriptStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManuscriptService {
    private final ManuscriptMapper manuscriptMapper;
    private final ManuscriptVersionMapper versionMapper;
    private final ManuscriptAuthorMapper authorMapper;
    private final ManuscriptTagMapper manuscriptTagMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final UserMapper userMapper;
    private final ReviewAssignmentMapper assignmentMapper;
    private final EditorialDecisionMapper decisionMapper;
    private final ReviewReportMapper reportMapper;
    private final RevisionMapper revisionMapper;
    private final CopyrightRecordMapper copyrightMapper;
    private final LlmTaskMapper llmTaskMapper;
    private final MetadataRevisionMapper metadataRevisionMapper;
    private final OperationLogMapper operationLogMapper;
    private final FileStorageService fileStorage;
    private final ManuscriptStateMachine stateMachine;
    private final NotificationService notificationService;

    // ---------------- 作者侧：创建/编辑/提交/修回/撤稿 ----------------

    @Transactional
    public Manuscript createDraft(Long uid, SaveRequest req) {
        requireCategory(req.getCategoryId());
        validateRelations(uid, req);
        Manuscript m = new Manuscript();
        m.setSubmitterId(uid);
        applySave(m, req);
        m.setStatus(ManuscriptStatus.DRAFT.name());
        m.setCreatedAt(LocalDateTime.now());
        manuscriptMapper.insert(m);
        saveAuthors(m.getId(), uid, req.getCoAuthors(), req.getSubmitterCorresponding());
        saveTags(m.getId(), req.getTagIds());
        return m;
    }

    @Transactional
    public void updateDraft(Long id, Long uid, SaveRequest req) {
        Manuscript m = getOwned(id, uid);
        if (!ManuscriptStatus.DRAFT.name().equals(m.getStatus())) {
            throw BizException.conflict("只有草稿状态可以编辑元数据");
        }
        requireCategory(req.getCategoryId());
        validateRelations(uid, req);
        applySave(m, req);
        manuscriptMapper.updateById(m);
        saveAuthors(m.getId(), uid, req.getCoAuthors(), req.getSubmitterCorresponding());
        saveTags(m.getId(), req.getTagIds());
    }

    @Transactional
    public void deleteDraft(Long id, Long uid) {
        Manuscript m = getOwned(id, uid);
        if (!ManuscriptStatus.DRAFT.name().equals(m.getStatus())) {
            throw BizException.conflict("只有草稿可以删除，已提交稿件请使用撤稿");
        }
        authorMapper.delete(new LambdaQueryWrapper<ManuscriptAuthor>().eq(ManuscriptAuthor::getManuscriptId, id));
        manuscriptTagMapper.delete(new LambdaQueryWrapper<ManuscriptTag>().eq(ManuscriptTag::getManuscriptId, id));
        manuscriptMapper.deleteById(id);
    }

    /** 初次投稿：草稿 → 已提交，生成 v1 版本 */
    @Transactional
    public ManuscriptVersion submit(Long id, Long uid, MultipartFile file) {
        Manuscript m = getOwned(id, uid);
        stateMachine.transition(m, ManuscriptStatus.SUBMITTED);
        return createVersion(m, uid, file, null);
    }

    /** 修回：退修 → 审稿中（复审），生成新版本 + 修改说明，并自动为原审稿人创建复审任务 */
    @Transactional
    public ManuscriptVersion revise(Long id, Long uid, MultipartFile file, String changeNote, String responseToReviewers) {
        Manuscript m = getOwned(id, uid);
        if (file == null || file.isEmpty()) {
            throw BizException.badParam("修回必须上传修改后的稿件文件");
        }
        Long basedVersionId = m.getCurrentVersionId();
        ManuscriptVersion based = versionMapper.selectById(basedVersionId);
        int newVersionNo = based == null ? 1 : based.getVersionNo() + 1;

        stateMachine.transition(m, ManuscriptStatus.UNDER_REVIEW);
        ManuscriptVersion created = createVersion(m, uid, file, changeNote);

        Revision rev = new Revision();
        rev.setManuscriptId(id);
        rev.setBasedVersionId(basedVersionId);
        rev.setNewVersionId(created.getId());
        rev.setResponseToReviewers(responseToReviewers);
        rev.setSubmittedAt(LocalDateTime.now());
        revisionMapper.insert(rev);

        // 原审稿人自动获得复审任务
        if (basedVersionId != null) {
            List<ReviewAssignment> prev = assignmentMapper.selectList(
                    new LambdaQueryWrapper<ReviewAssignment>()
                            .eq(ReviewAssignment::getManuscriptId, id)
                            .eq(ReviewAssignment::getVersionId, basedVersionId)
                            .in(ReviewAssignment::getStatus, List.of("ACCEPTED", "COMPLETED")));
            for (ReviewAssignment pa : prev) {
                ReviewAssignment na = new ReviewAssignment();
                na.setManuscriptId(id);
                na.setVersionId(created.getId());
                na.setReviewerId(pa.getReviewerId());
                na.setInvitedBy(pa.getInvitedBy());
                na.setInvitedAt(LocalDateTime.now());
                na.setDueAt(pa.getDueAt());
                na.setStatus("PENDING");
                try {
                    assignmentMapper.insert(na);
                } catch (Exception ignored) {
                    // 唯一键冲突时跳过
                }
                User reviewer = userMapper.selectById(pa.getReviewerId());
                notificationService.create(pa.getReviewerId(), "REVIEW_INVITE", "复审邀请：" + m.getTitle(),
                        "作者已按意见修回（v" + newVersionNo + "），请您复审新版本。", id);
            }
        }
        return created;
    }

    @Transactional
    public void withdraw(Long id, Long uid) {
        Manuscript m = getOwned(id, uid);
        ManuscriptStatus.of(m.getStatus()).checkTransition(ManuscriptStatus.WITHDRAWN);
        stateMachine.transition(m, ManuscriptStatus.WITHDRAWN);
        manuscriptMapper.updateById(m);
        // 通知参与过的编辑
        assignmentMapper.selectList(new LambdaQueryWrapper<ReviewAssignment>()
                        .eq(ReviewAssignment::getManuscriptId, id))
                .stream().map(ReviewAssignment::getInvitedBy).distinct()
                .forEach(editorId -> notificationService.create(editorId, "SYSTEM",
                        "稿件已撤回", "作者撤回了稿件《" + m.getTitle() + "》。", id));
    }

    // ---------------- 查询 ----------------

    public PageVO<Map<String, Object>> pageMy(Long uid, long page, long size, String status, String keyword) {
        return pageManuscripts(page, size, status, keyword, uid, null);
    }

    public PageVO<Map<String, Object>> pageAll(long page, long size, String status, String keyword, Long categoryId) {
        return pageManuscripts(page, size, status, keyword, null, categoryId);
    }

    private PageVO<Map<String, Object>> pageManuscripts(long page, long size, String status, String keyword,
                                                        Long submitterId, Long categoryId) {
        Page<Manuscript> p = new Page<>(page, size);
        LambdaQueryWrapper<Manuscript> w = new LambdaQueryWrapper<Manuscript>()
                .eq(submitterId != null, Manuscript::getSubmitterId, submitterId)
                .eq(status != null && !status.isBlank(), Manuscript::getStatus, status)
                .eq(categoryId != null, Manuscript::getCategoryId, categoryId)
                .like(keyword != null && !keyword.isBlank(), Manuscript::getTitle, keyword)
                .orderByDesc(Manuscript::getUpdatedAt);
        Page<Manuscript> result = manuscriptMapper.selectPage(p, w);

        Map<Long, String> categoryNames = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        Map<Long, String> userNames = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName));
        List<Map<String, Object>> rows = result.getRecords().stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("title", m.getTitle());
            row.put("status", m.getStatus());
            row.put("currentVersionId", m.getCurrentVersionId());
            row.put("categoryId", m.getCategoryId());
            row.put("categoryName", categoryNames.get(m.getCategoryId()));
            row.put("submitterId", m.getSubmitterId());
            row.put("submitterName", userNames.get(m.getSubmitterId()));
            row.put("keywords", m.getKeywords());
            row.put("createdAt", m.getCreatedAt());
            row.put("updatedAt", m.getUpdatedAt());
            row.put("publishedAt", m.getPublishedAt());
            ManuscriptVersion v = m.getCurrentVersionId() == null ? null : versionMapper.selectById(m.getCurrentVersionId());
            row.put("currentVersionNo", v == null ? null : v.getVersionNo());
            row.put("volume", m.getVolume());
            row.put("issue", m.getIssue());
            row.put("doi", m.getDoi());
            row.put("copyrightSigned", m.getCurrentVersionId() != null && copyrightMapper.selectCount(
                    new LambdaQueryWrapper<CopyrightRecord>()
                            .eq(CopyrightRecord::getManuscriptId, m.getId())
                            .eq(CopyrightRecord::getVersionId, m.getCurrentVersionId())) > 0);
            return row;
        }).toList();
        return PageVO.of(rows, result.getTotal(), page, size);
    }

    public Map<String, Object> detail(Long id, UserDetailsImpl user) {
        Manuscript m = manuscriptMapper.selectById(id);
        if (m == null) throw BizException.notFound("稿件不存在");
        boolean isEditorOrAdmin = hasRole(user, "EDITOR") || hasRole(user, "ADMIN");
        boolean isSubmitter = user != null && user.getId().equals(m.getSubmitterId());
        boolean isAssignedReviewer = user != null && assignmentMapper.selectCount(
                new LambdaQueryWrapper<ReviewAssignment>()
                        .eq(ReviewAssignment::getManuscriptId, id)
                        .eq(ReviewAssignment::getReviewerId, user.getId())) > 0;
        if (!isEditorOrAdmin && !isSubmitter && !isAssignedReviewer
                && !ManuscriptStatus.PUBLISHED.name().equals(m.getStatus())) {
            throw BizException.forbidden("无权查看该稿件");
        }
        boolean canSeeReviewProcess = isEditorOrAdmin || isAssignedReviewer
                || (isSubmitter && hasReviewDecision(id));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("manuscript", m);
        data.put("category", m.getCategoryId() == null ? null : categoryMapper.selectById(m.getCategoryId()));
        data.put("authors", authorMapper.selectList(new LambdaQueryWrapper<ManuscriptAuthor>()
                .eq(ManuscriptAuthor::getManuscriptId, id).orderByAsc(ManuscriptAuthor::getAuthorOrder)));
        data.put("tags", tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .inSql(Tag::getId, "SELECT tag_id FROM manuscript_tag WHERE manuscript_id = " + id)));
        data.put("versions", versionMapper.selectList(new LambdaQueryWrapper<ManuscriptVersion>()
                .eq(ManuscriptVersion::getManuscriptId, id).orderByAsc(ManuscriptVersion::getVersionNo)));
        data.put("currentVersion", m.getCurrentVersionId() == null ? null : versionMapper.selectById(m.getCurrentVersionId()));
        data.put("decisions", decisionMapper.selectByManuscript(id));
        if (canSeeReviewProcess) {
            List<Map<String, Object>> assignments = new ArrayList<>();
            if (m.getCurrentVersionId() != null) {
                assignments = assignmentMapper.selectByManuscriptVersion(id, m.getCurrentVersionId());
            }
            data.put("assignments", assignments);
            data.put("reports", reportsForViewer(id, user, isEditorOrAdmin, isSubmitter, isAssignedReviewer));
        }
        data.put("copyrightRecords", copyrightMapper.selectList(new LambdaQueryWrapper<CopyrightRecord>()
                .eq(CopyrightRecord::getManuscriptId, id).orderByDesc(CopyrightRecord::getSignedAt)));
        data.put("metadataRevisions", metadataRevisionMapper.selectList(new LambdaQueryWrapper<MetadataRevision>()
                .eq(MetadataRevision::getManuscriptId, id).orderByDesc(MetadataRevision::getCreatedAt)));
        return data;
    }

    public List<Map<String, Object>> timeline(Long id, UserDetailsImpl user) {
        Manuscript m = manuscriptMapper.selectById(id);
        if (m == null) throw BizException.notFound("稿件不存在");
        boolean isEditorOrAdmin = hasRole(user, "EDITOR") || hasRole(user, "ADMIN");
        boolean isSubmitter = user != null && user.getId().equals(m.getSubmitterId());
        boolean isAssignedReviewer = user != null && assignmentMapper.selectCount(
                new LambdaQueryWrapper<ReviewAssignment>()
                        .eq(ReviewAssignment::getManuscriptId, id)
                        .eq(ReviewAssignment::getReviewerId, user.getId())) > 0;
        if (!isEditorOrAdmin && !isSubmitter && !isAssignedReviewer
                && !ManuscriptStatus.PUBLISHED.name().equals(m.getStatus())) {
            throw BizException.forbidden("无权查看该稿件");
        }
        List<Map<String, Object>> items = new ArrayList<>();

        for (ManuscriptVersion v : versionMapper.selectList(new LambdaQueryWrapper<ManuscriptVersion>()
                .eq(ManuscriptVersion::getManuscriptId, id).orderByAsc(ManuscriptVersion::getVersionNo))) {
            boolean isRevision = v.getVersionNo() > 1;
            items.add(item(v.getCreatedAt(), "VERSION", (isRevision ? "修回生成 v" : "投稿 v") + v.getVersionNo(),
                    v.getFileName() + (v.getChangeNote() == null ? "" : "；修改说明：" + v.getChangeNote())));
        }
        for (Revision r : revisionMapper.selectList(new LambdaQueryWrapper<Revision>()
                .eq(Revision::getManuscriptId, id))) {
            items.add(item(r.getSubmittedAt(), "REVISION", "提交修改说明", r.getResponseToReviewers()));
        }
        if (isEditorOrAdmin || isAssignedReviewer) {
            for (ReviewAssignment a : assignmentMapper.selectList(new LambdaQueryWrapper<ReviewAssignment>()
                    .eq(ReviewAssignment::getManuscriptId, id))) {
                String reviewerName = maskReviewer(userMapper.selectById(a.getReviewerId()).getRealName(),
                        isEditorOrAdmin);
                items.add(item(a.getInvitedAt(), "REVIEW", "审稿邀请（v" + versionNoOf(a.getVersionId()) + "）",
                        reviewerName + " · " + assignmentStatusLabel(a.getStatus())));
            }
            for (ReviewReport r : reportMapper.selectList(new LambdaQueryWrapper<ReviewReport>()
                    .eq(ReviewReport::getManuscriptId, id))) {
                items.add(item(r.getSubmittedAt(), "REVIEW_REPORT", "收到审稿意见（v" + versionNoOf(r.getVersionId()) + "）",
                        "建议等级：" + recommendationLabel(r.getRecommendation())
                                + "，评分：创新性" + r.getScoreNovelty() + "/质量" + r.getScoreQuality() + "/清晰度" + r.getScoreClarity()));
            }
        }
        for (Map<String, Object> d : decisionMapper.selectByManuscript(id)) {
            items.add(item((LocalDateTime) d.get("decided_at"), "DECISION",
                    stageLabel((String) d.get("stage")) + "：" + decisionLabel((String) d.get("decision")),
                    (String) d.get("reason")));
        }
        if (isEditorOrAdmin) {
            for (LlmTask t : llmTaskMapper.selectList(new LambdaQueryWrapper<LlmTask>()
                    .eq(LlmTask::getManuscriptId, id).orderByAsc(LlmTask::getCreatedAt))) {
                items.add(item(t.getCreatedAt(), "LLM", "AI 辅助 · " + sceneLabel(t.getScene()),
                        "模板 " + t.getTemplateId() + " · " + t.getProvider() + "/" + t.getModel()
                                + " · 状态：" + t.getStatus()));
            }
        }
        for (CopyrightRecord c : copyrightMapper.selectList(new LambdaQueryWrapper<CopyrightRecord>()
                .eq(CopyrightRecord::getManuscriptId, id))) {
            items.add(item(c.getSignedAt(), "COPYRIGHT", "签署版权协议", "许可类型：" + c.getLicenseType()));
        }
        for (MetadataRevision r : metadataRevisionMapper.selectList(new LambdaQueryWrapper<MetadataRevision>()
                .eq(MetadataRevision::getManuscriptId, id))) {
            items.add(item(r.getCreatedAt(), "METADATA", "AI 元数据人工确认写回",
                    "版本记录 #" + r.getVersionId() + " · AI 任务 #" + r.getLlmTaskId()));
        }
        if (m.getPublishedAt() != null) {
            items.add(item(m.getPublishedAt(), "PUBLISH", "正式出版归档",
                    m.getVolume() + "卷" + m.getIssue() + "期 " + (m.getDoi() == null ? "" : m.getDoi())));
        }
        operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                        .eq(OperationLog::getObjectType, "MANUSCRIPT")
                        .eq(OperationLog::getObjectId, id))
                .forEach(l -> items.add(item(l.getCreatedAt(), "LOG", "操作：" + l.getAction(),
                        l.getSuccess() == 1 ? "成功" : "失败")));
        items.sort(Comparator.comparing((Map<String, Object> i) -> (LocalDateTime) i.get("time")).reversed());
        return items;
    }

    // ---------------- 内部工具 ----------------

    private ManuscriptVersion createVersion(Manuscript m, Long uid, MultipartFile file, String changeNote) {
        FileStorageService.StoredFile stored = fileStorage.store(file);
        cleanupFileOnRollback(stored.path());
        ManuscriptVersion latest = versionMapper.selectLatest(m.getId());
        int versionNo = latest == null ? 1 : latest.getVersionNo() + 1;
        ManuscriptVersion v = new ManuscriptVersion();
        v.setManuscriptId(m.getId());
        v.setVersionNo(versionNo);
        v.setTitle(m.getTitle());
        v.setAbstract_(m.getAbstract_());
        v.setKeywords(m.getKeywords());
        v.setFilePath(stored.path());
        v.setFileName(stored.name());
        v.setFileSize(stored.size());
        v.setFileHash(stored.sha256());
        v.setChangeNote(changeNote);
        v.setCreatedBy(uid);
        v.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(v);
        m.setCurrentVersionId(v.getId());
        manuscriptMapper.updateById(m);
        return v;
    }

    private void cleanupFileOnRollback(String storedPath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    try {
                        fileStorage.deleteIfExists(storedPath);
                    } catch (Exception ignored) {
                        // 主事务已经回滚，清理失败交由文件一致性巡检处理。
                    }
                }
            }
        });
    }

    private Manuscript getOwned(Long id, Long uid) {
        Manuscript m = manuscriptMapper.selectById(id);
        if (m == null) throw BizException.notFound("稿件不存在");
        if (!m.getSubmitterId().equals(uid)) {
            throw BizException.forbidden("只能操作本人的稿件");
        }
        return m;
    }

    private void applySave(Manuscript m, SaveRequest req) {
        m.setTitle(req.getTitle());
        m.setAbstract_(req.getAbstract_());
        m.setKeywords(req.getKeywords());
        m.setCategoryId(req.getCategoryId());
    }

    private void validateRelations(Long submitterId, SaveRequest req) {
        Set<Long> users = new HashSet<>(); users.add(submitterId);
        boolean corresponding = !Boolean.FALSE.equals(req.getSubmitterCorresponding());
        for (ManuscriptDtos.CoAuthor ca : req.getCoAuthors() == null ? List.<ManuscriptDtos.CoAuthor>of() : req.getCoAuthors()) {
            if (ca == null) throw BizException.badParam("共同作者不能为空");
            if (ca.getUserId() != null) {
                if (!users.add(ca.getUserId())) throw BizException.badParam("注册作者不能重复或与投稿人重复");
                if (userMapper.selectById(ca.getUserId()) == null) throw BizException.badParam("共同作者账号不存在");
            } else if (ca.getName() == null || ca.getName().isBlank()) throw BizException.badParam("共同作者姓名不能为空");
            corresponding |= Boolean.TRUE.equals(ca.getCorresponding());
        }
        if (!corresponding) throw BizException.badParam("请至少设置一位通讯作者");
        Set<Long> tags = new HashSet<>();
        for (Long tag : req.getTagIds() == null ? List.<Long>of() : req.getTagIds()) {
            if (tag == null || !tags.add(tag) || tagMapper.selectById(tag) == null) throw BizException.badParam("标签不存在或重复");
        }
    }

    private void saveAuthors(Long manuscriptId, Long submitterId, List<ManuscriptDtos.CoAuthor> coAuthors, Boolean submitterCorresponding) {
        authorMapper.delete(new LambdaQueryWrapper<ManuscriptAuthor>().eq(ManuscriptAuthor::getManuscriptId, manuscriptId));
        User submitter = userMapper.selectById(submitterId);
        ManuscriptAuthor first = new ManuscriptAuthor();
        first.setManuscriptId(manuscriptId);
        first.setUserId(submitterId);
        first.setAuthorName(submitter.getRealName());
        first.setAffiliation(submitter.getAffiliation());
        first.setAuthorOrder(1);
        first.setIsCorresponding(Boolean.FALSE.equals(submitterCorresponding) ? 0 : 1);
        authorMapper.insert(first);
        int order = 2;
        for (ManuscriptDtos.CoAuthor ca : coAuthors == null ? List.<ManuscriptDtos.CoAuthor>of() : coAuthors) {
            ManuscriptAuthor ma = new ManuscriptAuthor();
            ma.setManuscriptId(manuscriptId);
            ma.setUserId(ca.getUserId());
            ma.setAuthorName(ca.getUserId() != null
                    ? userMapper.selectById(ca.getUserId()).getRealName() : ca.getName());
            ma.setAffiliation(ca.getAffiliation());
            ma.setAuthorOrder(order++);
            ma.setIsCorresponding(Boolean.TRUE.equals(ca.getCorresponding()) ? 1 : 0);
            authorMapper.insert(ma);
        }
    }

    private void saveTags(Long manuscriptId, List<Long> tagIds) {
        manuscriptTagMapper.delete(new LambdaQueryWrapper<ManuscriptTag>().eq(ManuscriptTag::getManuscriptId, manuscriptId));
        for (Long tagId : tagIds == null ? List.<Long>of() : tagIds) {
            ManuscriptTag mt = new ManuscriptTag();
            mt.setManuscriptId(manuscriptId);
            mt.setTagId(tagId);
            manuscriptTagMapper.insert(mt);
        }
    }

    private void requireCategory(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw BizException.badParam("学科分类不存在");
        }
    }

    private boolean hasReviewDecision(Long manuscriptId) {
        return decisionMapper.selectCount(new LambdaQueryWrapper<EditorialDecision>()
                .eq(EditorialDecision::getManuscriptId, manuscriptId)
                .eq(EditorialDecision::getStage, "REVIEW")) > 0;
    }

    private List<Map<String, Object>> reportsForViewer(Long id, UserDetailsImpl user, boolean editor,
                                                       boolean submitter, boolean assignedReviewer) {
        List<ReviewReport> reports = reportMapper.selectList(new LambdaQueryWrapper<ReviewReport>()
                .eq(ReviewReport::getManuscriptId, id).orderByAsc(ReviewReport::getSubmittedAt));
        List<Map<String, Object>> visible = new ArrayList<>();
        int idx = 1;
        for (ReviewReport r : reports) {
            boolean own = user != null && assignmentMapper.selectById(r.getAssignmentId()) != null
                    && user.getId().equals(assignmentMapper.selectById(r.getAssignmentId()).getReviewerId());
            boolean allow = editor || own
                    || (submitter && hasReviewDecision(id))
                    || (assignedReviewer && hasReviewDecision(id) && false);
            if (allow) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", r.getId());
                row.put("versionId", r.getVersionId());
                row.put("versionNo", versionNoOf(r.getVersionId()));
                row.put("scoreNovelty", r.getScoreNovelty());
                row.put("scoreQuality", r.getScoreQuality());
                row.put("scoreClarity", r.getScoreClarity());
                row.put("commentsToAuthor", r.getCommentsToAuthor());
                row.put("recommendation", r.getRecommendation());
                row.put("recommendationLabel", recommendationLabel(r.getRecommendation()));
                row.put("submittedAt", r.getSubmittedAt());
                if (editor || own) {
                    row.put("commentsToEditor", r.getCommentsToEditor());
                    Long reviewerId = assignmentMapper.selectById(r.getAssignmentId()).getReviewerId();
                    row.put("reviewerName", maskReviewer(userMapper.selectById(reviewerId).getRealName(), editor));
                } else {
                    row.put("reviewerName", "审稿专家" + idx);
                }
                visible.add(row);
            }
            idx++;
        }
        return visible;
    }

    private String maskReviewer(String realName, boolean reveal) {
        return reveal ? realName : "审稿专家";
    }

    private Integer versionNoOf(Long versionId) {
        ManuscriptVersion v = versionMapper.selectById(versionId);
        return v == null ? null : v.getVersionNo();
    }

    private Map<String, Object> item(LocalDateTime time, String type, String title, String detail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("time", time);
        map.put("type", type);
        map.put("title", title);
        map.put("detail", detail);
        return map;
    }

    private boolean hasRole(UserDetailsImpl user, String role) {
        return user != null && user.getRoleCodes().contains(role);
    }

    public static String assignmentStatusLabel(String s) {
        return switch (s) {
            case "PENDING" -> "待接受";
            case "ACCEPTED" -> "已接受";
            case "DECLINED" -> "已拒绝";
            case "COMPLETED" -> "已完成";
            case "EXPIRED" -> "已超时";
            default -> s;
        };
    }

    public static String recommendationLabel(String r) {
        return switch (r) {
            case "ACCEPT" -> "建议录用";
            case "MINOR" -> "建议小修";
            case "MAJOR" -> "建议大修";
            case "REJECT" -> "建议退稿";
            default -> r;
        };
    }

    public static String stageLabel(String s) {
        return switch (s) {
            case "FORMAT" -> "形式审查";
            case "REVIEW" -> "审稿决定";
            case "APPEAL" -> "申诉终审";
            case "GOVERNANCE" -> "举报处置";
            case "PUBLICATION" -> "出版归档";
            default -> s;
        };
    }

    public static String decisionLabel(String d) {
        return switch (d) {
            case "PASS" -> "通过";
            case "FAIL" -> "未通过";
            case "ACCEPT" -> "录用";
            case "REVISE" -> "退修";
            case "REJECT" -> "退稿";
            case "OVERTURN" -> "申诉成立，决定撤销";
            case "RETRACT" -> "下架撤回";
            case "PUBLISHED" -> "正式发布";
            default -> d;
        };
    }

    public static String sceneLabel(String s) {
        return switch (s) {
            case "EXTRACT" -> "元数据抽取";
            case "SUMMARY" -> "意见汇总";
            case "EXPLAIN" -> "意见解释";
            default -> s;
        };
    }

    public static String statusLabel(String s) {
        return switch (s) {
            case "DRAFT" -> "草稿";
            case "SUBMITTED" -> "已提交";
            case "IN_FORMAT_REVIEW" -> "形式审查中";
            case "UNDER_REVIEW" -> "审稿中";
            case "PENDING_DECISION" -> "待编辑决定";
            case "ACCEPTED" -> "已录用";
            case "IN_PRODUCTION" -> "出版中";
            case "PUBLISHED" -> "已出版";
            case "REVISION_REQUIRED" -> "退修";
            case "REJECTED" -> "已退稿";
            case "WITHDRAWN" -> "已撤稿";
            default -> s;
        };
    }
}
