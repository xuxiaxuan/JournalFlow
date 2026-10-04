package com.journalflow.controller;

import com.journalflow.common.GlobalExceptionHandler;
import com.journalflow.common.PageVO;
import com.journalflow.config.MybatisPlusConfig;
import com.journalflow.config.SecurityConfig;
import com.journalflow.entity.LlmTask;
import com.journalflow.entity.Manuscript;
import com.journalflow.mapper.ManuscriptMapper;
import com.journalflow.mapper.EditorialDecisionMapper;
import com.journalflow.security.JwtAuthFilter;
import com.journalflow.security.UserDetailsImpl;
import com.journalflow.service.LlmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = LlmController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthFilter.class, MybatisPlusConfig.class, SecurityConfig.class}))
@ContextConfiguration(classes = {
        LlmController.class, GlobalExceptionHandler.class, TestSecurityConfig.class
})
class LlmControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @MockBean LlmService llmService;
    @MockBean ManuscriptMapper manuscriptMapper;
    @MockBean EditorialDecisionMapper decisionMapper;

    @Test
    void authorCannotStartExtraction() throws Exception {
        mvc.perform(post("/api/llm/tasks")
                        .with(user(principal(101L, "AUTHOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scene\":\"EXTRACT\",\"manuscriptId\":8}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void editorCanStartExtraction() throws Exception {
        LlmTask task = task(15L, "EXTRACT", "SUCCESS");
        when(llmService.createTask("EXTRACT", 8L, 301L)).thenReturn(task);

        mvc.perform(post("/api/llm/tasks")
                        .with(user(principal(301L, "EDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scene\":\"EXTRACT\",\"manuscriptId\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(15));
        verify(llmService).createTask("EXTRACT", 8L, 301L);
    }

    @Test
    void manuscriptOwnerCanStartExplanation() throws Exception {
        Manuscript manuscript = new Manuscript();
        manuscript.setId(8L);
        manuscript.setSubmitterId(101L);
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript);
        when(decisionMapper.selectCount(any())).thenReturn(1L);
        when(llmService.createTask("EXPLAIN", 8L, 101L)).thenReturn(task(16L, "EXPLAIN", "SUCCESS"));

        mvc.perform(post("/api/llm/tasks")
                        .with(user(principal(101L, "AUTHOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scene\":\"EXPLAIN\",\"manuscriptId\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scene").value("EXPLAIN"));
    }

    @Test
    void nonOwnerCannotStartExplanation() throws Exception {
        Manuscript manuscript = new Manuscript();
        manuscript.setSubmitterId(999L);
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript);

        mvc.perform(post("/api/llm/tasks")
                        .with(user(principal(101L, "AUTHOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scene\":\"EXPLAIN\",\"manuscriptId\":8}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCannotStartExplanationBeforeEditorialDecision() throws Exception {
        Manuscript manuscript = new Manuscript();
        manuscript.setId(8L);
        manuscript.setSubmitterId(101L);
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript);
        when(decisionMapper.selectCount(any())).thenReturn(0L);

        mvc.perform(post("/api/llm/tasks")
                        .with(user(principal(101L, "AUTHOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scene\":\"EXPLAIN\",\"manuscriptId\":8}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void unsupportedSceneReturns400() throws Exception {
        mvc.perform(post("/api/llm/tasks")
                        .with(user(principal(301L, "EDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scene\":\"TRANSLATE\",\"manuscriptId\":8}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void authorCannotConfirmAiOutput() throws Exception {
        mvc.perform(post("/api/llm/tasks/15/confirm")
                        .with(user(principal(101L, "AUTHOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmedOutput\":\"{}\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void blankConfirmationReturns400() throws Exception {
        mvc.perform(post("/api/llm/tasks/15/confirm")
                        .with(user(principal(301L, "EDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmedOutput\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editorCanConfirmRevisedAiOutput() throws Exception {
        LlmTask confirmed = task(15L, "EXTRACT", "CONFIRMED");
        confirmed.setConfirmedBy(301L);
        when(llmService.confirm(15L, 301L, "{\"title\":\"人工修订标题\"}"))
                .thenReturn(confirmed);

        mvc.perform(post("/api/llm/tasks/15/confirm")
                        .with(user(principal(301L, "EDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmedOutput\":\"{\\\"title\\\":\\\"人工修订标题\\\"}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedBy").value(301));
    }

    @Test
    void authorTaskListIsRestrictedToOwnExplanationTasks() throws Exception {
        when(llmService.pageTasks(1, 10, "EXPLAIN", null, null, 101L))
                .thenReturn(PageVO.of(java.util.List.of(), 0, 1, 10));

        mvc.perform(get("/api/llm/tasks")
                        .with(user(principal(101L, "AUTHOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        verify(llmService).pageTasks(1, 10, "EXPLAIN", null, null, 101L);
    }

    @Test
    void authorCannotReadEditorsSummaryTaskByGuessingId() throws Exception {
        LlmTask summary = task(15L, "SUMMARY", "CONFIRMED");
        summary.setManuscriptId(8L);
        summary.setCreatedBy(301L);
        when(llmService.get(15L)).thenReturn(summary);

        mvc.perform(get("/api/llm/tasks/15")
                        .with(user(principal(101L, "AUTHOR"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void authorCanReadOwnExplanationTask() throws Exception {
        LlmTask explanation = task(16L, "EXPLAIN", "SUCCESS");
        explanation.setManuscriptId(8L);
        explanation.setCreatedBy(101L);
        when(llmService.get(16L)).thenReturn(explanation);

        mvc.perform(get("/api/llm/tasks/16")
                        .with(user(principal(101L, "AUTHOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scene").value("EXPLAIN"));
    }

    @Test
    void authorCannotRetryAnotherUsersFailedExplanation() throws Exception {
        LlmTask failed = task(16L, "EXPLAIN", "FAILED");
        failed.setManuscriptId(8L);
        failed.setCreatedBy(999L);
        Manuscript manuscript = new Manuscript();
        manuscript.setId(8L);
        manuscript.setSubmitterId(101L);
        when(llmService.get(16L)).thenReturn(failed);
        when(manuscriptMapper.selectById(8L)).thenReturn(manuscript);
        when(decisionMapper.selectCount(any())).thenReturn(1L);

        mvc.perform(post("/api/llm/tasks/16/retry")
                        .with(user(principal(101L, "AUTHOR"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    private LlmTask task(Long id, String scene, String status) {
        LlmTask task = new LlmTask();
        task.setId(id);
        task.setScene(scene);
        task.setStatus(status);
        return task;
    }

    private UserDetailsImpl principal(Long id, String role) {
        return UserDetailsImpl.of(id, "test-" + id, "password", 1, java.util.Set.of(role));
    }
}
