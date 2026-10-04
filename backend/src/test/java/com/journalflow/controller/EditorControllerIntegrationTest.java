package com.journalflow.controller;

import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.common.GlobalExceptionHandler;
import com.journalflow.config.MybatisPlusConfig;
import com.journalflow.config.SecurityConfig;
import com.journalflow.mapper.CopyrightRecordMapper;
import com.journalflow.mapper.UserMapper;
import com.journalflow.security.JwtAuthFilter;
import com.journalflow.security.UserDetailsImpl;
import com.journalflow.service.EditorFlowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = EditorController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthFilter.class, MybatisPlusConfig.class, SecurityConfig.class}))
@ContextConfiguration(classes = {
        EditorController.class, GlobalExceptionHandler.class, TestSecurityConfig.class
})
class EditorControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @MockBean EditorFlowService editorFlowService;
    @MockBean CopyrightRecordMapper copyrightRecordMapper;
    @MockBean UserMapper userMapper;

    @Test
    void authorCannotMakeEditorialDecision() throws Exception {
        mvc.perform(post("/api/editor/manuscripts/8/decision")
                        .with(user(principal(101L, "AUTHOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void blankDecisionReturns400() throws Exception {
        mvc.perform(post("/api/editor/manuscripts/8/decision")
                        .with(user(principal(301L, "EDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void editorCanMakeDecisionWithConfirmedSummaryReference() throws Exception {
        mvc.perform(post("/api/editor/manuscripts/8/decision")
                        .with(user(principal(301L, "EDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"REVISE","reason":"补充实验后复审",
                                 "summaryReport":"人工确认后的审稿汇总","llmTaskId":15}
                                """))
                .andExpect(status().isOk());
        verify(editorFlowService).decide(eq(8L), eq(301L), any());
    }

    @Test
    void incompletePublicationMetadataReturns400() throws Exception {
        mvc.perform(post("/api/editor/manuscripts/8/publish")
                        .with(user(principal(301L, "EDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volume\":\"\",\"issue\":\"3\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidDoiReturns400BeforePublicationServiceIsCalled() throws Exception {
        mvc.perform(post("/api/editor/manuscripts/8/publish")
                        .with(user(principal(301L, "EDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volume\":\"12\",\"issue\":\"3\",\"pages\":\"1-12\",\"doi\":\"not-a-doi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void publicationWithoutCopyrightReturns409() throws Exception {
        doThrow(new BizException(ErrorCode.CONFLICT, "当前稿件版本尚未签署版权协议"))
                .when(editorFlowService).publish(eq(8L), eq(301L), any());

        mvc.perform(post("/api/editor/manuscripts/8/publish")
                        .with(user(principal(301L, "EDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volume\":\"12\",\"issue\":\"3\",\"pages\":\"1-12\",\"doi\":\"10.1234/jf.2026.8\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value("当前稿件版本尚未签署版权协议"));
    }

    private UserDetailsImpl principal(Long id, String role) {
        return UserDetailsImpl.of(id, "test-" + id, "password", 1, java.util.Set.of(role));
    }
}
