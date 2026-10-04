package com.journalflow.controller;

import com.journalflow.common.GlobalExceptionHandler;
import com.journalflow.entity.Appeal;
import com.journalflow.entity.Report;
import com.journalflow.security.JwtAuthFilter;
import com.journalflow.security.UserDetailsImpl;
import com.journalflow.service.AppealService;
import com.journalflow.service.ReportService;
import com.journalflow.config.MybatisPlusConfig;
import com.journalflow.config.SecurityConfig;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = GovernanceController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthFilter.class, MybatisPlusConfig.class, SecurityConfig.class}))
@ContextConfiguration(classes = {
        GovernanceController.class, GlobalExceptionHandler.class, TestSecurityConfig.class
})
class GovernanceControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @MockBean ReportService reportService;
    @MockBean AppealService appealService;

    @Test
    void invalidReportReturns400() throws Exception {
        mvc.perform(post("/api/reports")
                        .with(user(principal(101L, "AUTHOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"COMMENT\",\"targetId\":9,\"reasonType\":\"ABUSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void authenticatedUserCanCreateReport() throws Exception {
        Report report = new Report();
        report.setId(21L);
        report.setStatus("PENDING");
        when(reportService.create(eq(101L), any())).thenReturn(report);

        mvc.perform(post("/api/reports")
                        .with(user(principal(101L, "AUTHOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"COMMENT","targetId":9,"reasonType":"ABUSE",
                                 "description":"该评论包含人身攻击"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(21))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        verify(reportService).create(eq(101L), any());
    }

    @Test
    void authorCannotHandleReport() throws Exception {
        mvc.perform(post("/api/reports/21/handle")
                        .with(user(principal(101L, "AUTHOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"resolution\":\"删除违规评论\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispositionWithoutExplanationReturns400() throws Exception {
        mvc.perform(post("/api/reports/21/handle")
                        .with(user(principal(301L, "EDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"resolution\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void editorCanHandleReportAsNonAdmin() throws Exception {
        mvc.perform(post("/api/reports/21/handle")
                        .with(user(principal(301L, "EDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"resolution\":\"删除违规评论\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(reportService).handle(eq(21L), eq(301L), eq(false), any());
    }

    @Test
    void userCanCreateAppeal() throws Exception {
        Appeal appeal = new Appeal();
        appeal.setId(31L);
        appeal.setStatus("PENDING");
        when(appealService.create(eq(101L), any())).thenReturn(appeal);

        mvc.perform(post("/api/appeals")
                        .with(user(principal(101L, "AUTHOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportId\":21,\"content\":\"处置事实认定有误\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(31));
    }

    @Test
    void onlyAdminCanHandleAppeal() throws Exception {
        String body = "{\"status\":\"DISMISSED\",\"result\":\"原处置证据充分\"}";
        mvc.perform(post("/api/appeals/31/handle")
                        .with(user(principal(301L, "EDITOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/appeals/31/handle")
                        .with(user(principal(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(appealService).handle(eq(31L), eq(1L), any());
    }

    private UserDetailsImpl principal(Long id, String role) {
        return UserDetailsImpl.of(id, "test-" + id, "password", 1, java.util.Set.of(role));
    }
}
