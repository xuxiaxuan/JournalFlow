package com.journalflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.journalflow.dto.ManuscriptDtos.*;
import com.journalflow.dto.EditorDtos.*;
import com.journalflow.dto.ReviewDtos.*;
import com.journalflow.entity.*;
import com.journalflow.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real MySQL test: all business writes roll back, uploaded files use a temporary directory. */
@EnabledIfSystemProperty(named = "journal.mysql.tests", matches = "true")
@SpringBootTest(properties = {"journal.llm.recovery-enabled=false", "journal.llm.provider=mock",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
@Transactional
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class MySqlWorkflowTest {
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    static Path uploadDir;
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        uploadDir = Files.createTempDirectory("journalflow-workflow-");
        registry.add("journal.upload-dir", () -> uploadDir.toString());
    }
    @Autowired ManuscriptService manuscripts;
    @Autowired EditorFlowService editors;
    @Autowired ReviewFlowService reviews;
    @Autowired AdminService admin;
    @Autowired LlmService llm;
    @Autowired ManuscriptMapper manuscriptMapper;
    @Autowired ReviewAssignmentMapper assignmentMapper;
    @Autowired CategoryMapper categoryMapper;
    @Autowired UserMapper userMapper;
    @Autowired LlmTaskMapper taskMapper;
    @Autowired PublicService publicService;
    @Autowired ResearchToolsService researchTools;

    Long user(String name) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, name));
        assertNotNull(user, "请先初始化项目演示账号");
        return user.getId();
    }
    MockMultipartFile file() {
        return new MockMultipartFile("file", "workflow.tex", "text/plain",
                "真实稿件正文\n参考文献\n[1] Sample reference".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    ReportRequest report() {
        ReportRequest r = new ReportRequest();
        r.setScoreNovelty(8); r.setScoreQuality(8); r.setScoreClarity(8);
        r.setCommentsToAuthor("请完善实验说明"); r.setRecommendation("MINOR");
        return r;
    }
    void decide(Long id, Long editor, String value) {
        DecisionRequest request = new DecisionRequest(); request.setDecision(value); request.setReason("集成测试决定");
        editors.decide(id, editor, request);
    }
    @Test
    void submissionRevisionReviewCopyrightAndPublicationRoundTrip() {
        Long author = user("author1"), editor = user("editor1"), reviewer = user("reviewer1");
        Category category = categoryMapper.selectList(null).get(0);
        SaveRequest draft = new SaveRequest();
        String title = "集成测试-" + UUID.randomUUID();
        draft.setTitle(title); draft.setAbstract_("真实数据库流程测试");
        draft.setKeywords("测试;期刊;审稿"); draft.setCategoryId(category.getId());
        Manuscript manuscript = manuscripts.createDraft(author, draft);
        Long id = manuscript.getId();
        ManuscriptVersion first = manuscripts.submit(id, author, file());
        assertEquals(draft.getAbstract_(), manuscriptMapper.selectById(id).getAbstract_());
        assertEquals(draft.getAbstract_(), first.getAbstract_());
        assertEquals(draft.getAbstract_(), versionMapper.selectById(first.getId()).getAbstract_());
        assertEquals(draft.getAbstract_(), versionMapper.selectLatest(id).getAbstract_());
        assertEquals("SUBMITTED", manuscriptMapper.selectById(id).getStatus());
        assertEquals(1, editors.formatQueue(1, 10, null, title).getTotal());
        editors.startFormatReview(id, editor);
        FormatReviewRequest format = new FormatReviewRequest(); format.setPass(true);
        editors.completeFormatReview(id, editor, format);
        AssignReviewersRequest assignment = new AssignReviewersRequest(); assignment.setReviewerIds(List.of(reviewer));
        ReviewAssignment a = editors.assignReviewers(id, editor, assignment).get(0);
        reviews.accept(a.getId(), reviewer); reviews.submitReport(a.getId(), reviewer, report());
        assertEquals("PENDING_DECISION", manuscriptMapper.selectById(id).getStatus());
        decide(id, editor, "REVISE");
        ManuscriptVersion second = manuscripts.revise(id, author, file(), "完善实验", "已逐项回应");
        assertEquals(2, second.getVersionNo()); assertNotEquals(first.getId(), second.getId());
        ReviewAssignment followup = assignmentMapper.selectOne(new LambdaQueryWrapper<ReviewAssignment>()
                .eq(ReviewAssignment::getVersionId, second.getId()));
        reviews.accept(followup.getId(), reviewer); reviews.submitReport(followup.getId(), reviewer, report());
        LlmTask summary = llm.createTask("SUMMARY", id, editor);
        // The enclosing test transaction never commits: explicitly run the worker body to exercise real persistence.
        llm.executeTask(summary.getId(), "只输出JSON", "意见1：建议等级：ACCEPT\n给作者：实验充分");
        LlmTask completed = llm.get(summary.getId()); assertEquals("SUCCESS", completed.getStatus());
        llm.confirm(summary.getId(), editor, completed.getParsedOutput());
        DecisionRequest accept = new DecisionRequest(); accept.setDecision("ACCEPT"); accept.setReason("同意录用");
        accept.setLlmTaskId(summary.getId()); editors.decide(id, editor, accept);
        CopyrightRequest copyright = new CopyrightRequest(); copyright.setAgreementText("测试作者确认授权");
        assertEquals(second.getId(), editors.signCopyright(id, author, copyright).getVersionId());
        PublishRequest publish = new PublishRequest(); publish.setVolume("1"); publish.setIssue("1");
        publish.setPages("1-10"); publish.setDoi("10.99999/test-" + UUID.randomUUID());
        editors.publish(id, editor, publish); editors.archive(id, editor);
        assertEquals("PUBLISHED", manuscriptMapper.selectById(id).getStatus());
        assertEquals(title, publicService.detail(id, author).get("title"));
        var published = publicService.publishedPage(1, 10, title, null, null, null, "hot").getList();
        assertEquals(author, ((Number) published.get(0).get("submitter_id")).longValue());
        assertTrue(publicService.recommend(author, 100).stream()
                .noneMatch(row -> author.equals(((Number) row.get("submitter_id")).longValue())));
    }

    @Test
    void optionalResearchToolsPreserveWorkflowAndResponseHistory() throws Exception {
        Long author = user("author1"), editor = user("editor1"), reviewer = user("reviewer1");
        var authorUser = com.journalflow.security.UserDetailsImpl.of(author, "author1", "unused", 1, java.util.Set.of("AUTHOR"));
        var editorUser = com.journalflow.security.UserDetailsImpl.of(editor, "editor1", "unused", 1, java.util.Set.of("EDITOR"));
        SaveRequest draft = new SaveRequest(); draft.setTitle("溯源测试-" + UUID.randomUUID());
        draft.setAbstract_("表单摘要"); draft.setKeywords("测试;期刊;审稿"); draft.setCategoryId(categoryMapper.selectList(null).get(0).getId());
        Long id = manuscripts.createDraft(author, draft).getId();
        String firstText = draft.getTitle() + "\n原有实验\n共同结论";
        ManuscriptVersion first = manuscripts.submit(id, author, new MockMultipartFile("file", "source.tex", "text/plain", firstText.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        LlmTask extraction = llm.createTask("EXTRACT", id, editor);
        llm.executeTask(extraction.getId(), "只输出JSON", "【标题】" + draft.getTitle() + "【摘要】表单摘要【关键词】测试;期刊;审稿【正文】" + firstText);
        assertEquals("SUCCESS", llm.get(extraction.getId()).getStatus());
        var evidence = researchTools.evidence(extraction.getId(), editorUser);
        assertEquals(first.getId(), evidence.get("versionId"));
        assertTrue(evidence.get("fields").toString().contains("source=BODY"));
        assertTrue(evidence.get("fields").toString().contains("source=VERSION_METADATA"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/llm/tasks/{id}/evidence", extraction.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(authorUser)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        editors.startFormatReview(id, editor);
        FormatReviewRequest format = new FormatReviewRequest(); format.setPass(true); editors.completeFormatReview(id, editor, format);
        AssignReviewersRequest assignment = new AssignReviewersRequest(); assignment.setReviewerIds(List.of(reviewer));
        ReviewAssignment a = editors.assignReviewers(id, editor, assignment).get(0);
        reviews.accept(a.getId(), reviewer);
        ReportRequest report = report(); report.setCommentsToAuthor("补充实验\n解释结论"); report.setCommentsToEditor("编辑保密意见");
        reviews.submitReport(a.getId(), reviewer, report);
        assertTrue(researchTools.checklist(id, authorUser).isEmpty());
        decide(id, editor, "REVISE");
        var checklist = researchTools.checklist(id, authorUser);
        assertEquals(2, checklist.size()); assertFalse(checklist.toString().contains("编辑保密意见"));
        Long reportId = (Long) checklist.get(0).get("reportId");
        ReviewResponse reply = researchTools.save(id, reportId, 0, "已补充实验", "第2行", first.getId(), null, authorUser);
        ReviewResponse confirmation = researchTools.confirm(id, reply.getId(), editorUser);
        assertEquals("CONFIRMED", confirmation.getStatus()); assertNotEquals(reply.getId(), confirmation.getId());
        assertEquals("REVISION_REQUIRED", manuscriptMapper.selectById(id).getStatus());
        String revisedText = draft.getTitle() + "\n新增对比实验\n共同结论";
        ManuscriptVersion second = manuscripts.revise(id, author, new MockMultipartFile("file", "revised.tex", "text/plain", revisedText.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "补充实验", "已修改");
        var diff = researchTools.compare(id, first.getId(), second.getId(), authorUser);
        assertTrue(diff.get("changes").toString().contains("ADDED")); assertTrue(diff.get("changes").toString().contains("REMOVED"));
        ReviewResponse edited = researchTools.save(id, reportId, 0, "已在修回稿补充对比实验", "第2行", second.getId(), confirmation.getId(), authorUser);
        assertEquals("RESPONDED", edited.getStatus());
        assertEquals(3, ((List<?>) researchTools.checklist(id, authorUser).get(0).get("history")).size());
        assertThrows(com.journalflow.common.BizException.class, () -> researchTools.confirm(id, reply.getId(), editorUser));
        assertThrows(com.journalflow.common.BizException.class, () -> researchTools.save(id, reportId, 0, "旧页面覆盖", "", second.getId(), reply.getId(), authorUser));
        assertThrows(com.journalflow.common.BizException.class, () -> researchTools.confirm(id, edited.getId(), authorUser));
        assertEquals("UNDER_REVIEW", manuscriptMapper.selectById(id).getStatus());
        assertEquals(draft.getTitle(), manuscriptMapper.selectById(id).getTitle());
        assertEquals("SUCCESS", llm.get(extraction.getId()).getStatus());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/manuscripts/{id}/compare", id)
                .param("from", first.getId().toString()).param("to", second.getId().toString())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(authorUser)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    @Test
    void multipartUploadAndAuthenticatedDownloadRoundTrip() throws Exception {
        Long author = user("author1");
        var principal = com.journalflow.security.UserDetailsImpl.of(author, "author1", "unused", 1, java.util.Set.of("AUTHOR"));
        SaveRequest draft = new SaveRequest(); draft.setTitle("上传接口测试-" + UUID.randomUUID());
        draft.setCategoryId(categoryMapper.selectList(null).get(0).getId());
        Long id = manuscripts.createDraft(author, draft).getId();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/manuscripts/{id}/submit", id)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/manuscripts/{id}/submit", id)
                .file(file()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        Long version = manuscriptMapper.selectById(id).getCurrentVersionId();
        assertNotNull(version);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/manuscripts/{id}/files/{version}", id, version)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(file().getBytes()));
    }

    @Test
    void coauthorAndTagUpdatesValidateBeforeChangingTheDraft() {
        Long author = user("author1");
        SaveRequest req = new SaveRequest(); req.setTitle("作者标签测试-" + UUID.randomUUID());
        req.setCategoryId(categoryMapper.selectList(null).get(0).getId());
        var tag = tagMapper.selectList(null).get(0);
        req.setTagIds(List.of(tag.getId()));
        com.journalflow.dto.ManuscriptDtos.CoAuthor ca = new com.journalflow.dto.ManuscriptDtos.CoAuthor();
        ca.setName("模拟共同作者"); ca.setAffiliation("模拟大学"); ca.setCorresponding(true);
        req.setCoAuthors(List.of(ca)); req.setSubmitterCorresponding(false);
        Long id = manuscripts.createDraft(author, req).getId();
        var people = authorMapper.selectList(new LambdaQueryWrapper<ManuscriptAuthor>()
                .eq(ManuscriptAuthor::getManuscriptId, id).orderByAsc(ManuscriptAuthor::getAuthorOrder));
        assertEquals(0, people.get(0).getIsCorresponding()); assertEquals(1, people.get(1).getIsCorresponding());
        req.setTagIds(List.of(Long.MAX_VALUE));
        assertThrows(com.journalflow.common.BizException.class, () -> manuscripts.updateDraft(id, author, req));
        assertEquals(2, authorMapper.selectCount(new LambdaQueryWrapper<ManuscriptAuthor>().eq(ManuscriptAuthor::getManuscriptId, id)));
        req.setTagIds(List.of(tag.getId())); ca.setCorresponding(false);
        assertThrows(com.journalflow.common.BizException.class, () -> manuscripts.updateDraft(id, author, req));
    }

    @Test
    void categoryParentCanBeClearedAndMalformedOutputCanBeStored() {
        Category parent = admin.createCategory("父-" + UUID.randomUUID(), null, 0);
        Category child = admin.createCategory("子-" + UUID.randomUUID(), parent.getId(), 0);
        admin.updateCategory(child.getId(), child.getName(), null, 0);
        assertNull(categoryMapper.selectById(child.getId()).getParentId());
        LlmTask existing = taskMapper.selectList(null).get(0);
        existing.setRawOutput("```json\nnot valid JSON\n```"); taskMapper.updateById(existing);
        assertEquals(existing.getRawOutput(), taskMapper.selectById(existing.getId()).getRawOutput());
    }

    @Autowired TagMapper tagMapper;
    @Autowired ManuscriptAuthorMapper authorMapper;
    @Autowired ManuscriptVersionMapper versionMapper;
}
