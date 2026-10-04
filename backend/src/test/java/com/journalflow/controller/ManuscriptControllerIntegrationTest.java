package com.journalflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.common.GlobalExceptionHandler;
import com.journalflow.entity.Manuscript;
import com.journalflow.entity.CopyrightRecord;
import com.journalflow.service.EditorFlowService;
import com.journalflow.service.FileStorageService;
import com.journalflow.service.ManuscriptService;
import com.journalflow.security.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ContextConfiguration;
import com.journalflow.security.JwtAuthFilter;
import com.journalflow.config.MybatisPlusConfig;
import com.journalflow.config.SecurityConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ManuscriptController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthFilter.class, MybatisPlusConfig.class, SecurityConfig.class}))
@ContextConfiguration(classes = {
        ManuscriptController.class, GlobalExceptionHandler.class, TestSecurityConfig.class
})
class ManuscriptControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ManuscriptService manuscriptService;
    @MockBean FileStorageService fileStorageService;
    @MockBean EditorFlowService editorFlowService;

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mvc.perform(post("/api/manuscripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reviewerCannotCreateAuthorManuscript() throws Exception {
        mvc.perform(post("/api/manuscripts")
                        .with(user(principal(303L, "REVIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidDraftReturns400WithUnifiedErrorCode() throws Exception {
        mvc.perform(post("/api/manuscripts")
                        .with(user(principal(101L, "AUTHOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"categoryId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void authorCanCreateValidDraft() throws Exception {
        Manuscript created = new Manuscript();
        created.setId(88L);
        created.setTitle("可追溯审稿流程研究");
        created.setStatus("DRAFT");
        when(manuscriptService.createDraft(eq(101L), any())).thenReturn(created);

        mvc.perform(post("/api/manuscripts")
                        .with(user(principal(101L, "AUTHOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(88))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        verify(manuscriptService).createDraft(eq(101L), any());
    }

    @Test
    void illegalWorkflowOperationReturns409() throws Exception {
        doThrow(new BizException(ErrorCode.CONFLICT, "草稿不能撤回"))
                .when(manuscriptService).withdraw(7L, 101L);

        mvc.perform(post("/api/manuscripts/7/withdraw")
                        .with(user(principal(101L, "AUTHOR"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value("草稿不能撤回"));
        verify(manuscriptService).withdraw(7L, 101L);
    }

    @Test
    void reviewerCannotSignCopyrightForAuthor() throws Exception {
        mvc.perform(post("/api/manuscripts/8/copyright")
                        .with(user(principal(202L, "REVIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreementText\":\"本人同意 CC-BY-4.0 协议\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void blankCopyrightAgreementReturns400() throws Exception {
        mvc.perform(post("/api/manuscripts/8/copyright")
                        .with(user(principal(101L, "AUTHOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licenseType\":\"CC-BY-4.0\",\"agreementText\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void authorCanSignCopyrightForCurrentVersion() throws Exception {
        CopyrightRecord record = new CopyrightRecord();
        record.setId(41L);
        record.setManuscriptId(8L);
        record.setVersionId(5L);
        record.setSignedBy(101L);
        when(editorFlowService.signCopyright(eq(8L), eq(101L), any())).thenReturn(record);

        mvc.perform(post("/api/manuscripts/8/copyright")
                        .with(user(principal(101L, "AUTHOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licenseType\":\"CC-BY-4.0\",\"agreementText\":\"本人同意版权协议\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value(5))
                .andExpect(jsonPath("$.data.signedBy").value(101));
        verify(editorFlowService).signCopyright(eq(8L), eq(101L), any());
    }

    @Test
    void invalidPaginationReturns400BeforeQueryExecution() throws Exception {
        mvc.perform(get("/api/manuscripts/my")
                        .with(user(principal(101L, "AUTHOR")))
                        .param("page", "0")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verify(manuscriptService, org.mockito.Mockito.never())
                .pageMy(any(), any(Long.class), any(Long.class), any(), any());
    }

    private String validRequest() throws Exception {
        return json.writeValueAsString(java.util.Map.of(
                "title", "可追溯审稿流程研究",
                "abstract_", "研究稿件版本与审核记录的可追溯机制。",
                "keywords", "期刊;审稿;可追溯",
                "categoryId", 2));
    }

    private UserDetailsImpl principal(Long id, String role) {
        return UserDetailsImpl.of(id, "test-" + id, "password", 1, java.util.Set.of(role));
    }
}
