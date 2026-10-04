package com.journalflow.controller;

import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.common.GlobalExceptionHandler;
import com.journalflow.entity.ReviewReport;
import com.journalflow.service.ReviewFlowService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ReviewController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthFilter.class, MybatisPlusConfig.class, SecurityConfig.class}))
@ContextConfiguration(classes = {
        ReviewController.class, GlobalExceptionHandler.class, TestSecurityConfig.class
})
class ReviewControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @MockBean ReviewFlowService reviewFlowService;

    @Test
    void authorCannotOperateReviewAssignment() throws Exception {
        mvc.perform(post("/api/reviews/assignments/3/accept")
                        .with(user(principal(101L, "AUTHOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewerCanAcceptOwnAssignment() throws Exception {
        mvc.perform(post("/api/reviews/assignments/3/accept")
                        .with(user(principal(202L, "REVIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(reviewFlowService).accept(3L, 202L);
    }

    @Test
    void invalidScoresReturn400() throws Exception {
        mvc.perform(post("/api/reviews/assignments/3/report")
                        .with(user(principal(202L, "REVIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scoreNovelty":11,"scoreQuality":0,"scoreClarity":8,
                                 "commentsToAuthor":"请补充实验","recommendation":"MINOR"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void validStructuredReviewIsReturned() throws Exception {
        ReviewReport report = new ReviewReport();
        report.setId(12L);
        report.setRecommendation("MINOR");
        when(reviewFlowService.submitReport(eq(3L), eq(202L), any())).thenReturn(report);

        mvc.perform(post("/api/reviews/assignments/3/report")
                        .with(user(principal(202L, "REVIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validReport()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(12))
                .andExpect(jsonPath("$.data.recommendation").value("MINOR"));
    }

    @Test
    void duplicateReviewReturns409() throws Exception {
        doThrow(new BizException(ErrorCode.CONFLICT, "该任务已提交过审稿意见"))
                .when(reviewFlowService).submitReport(eq(3L), eq(202L), any());

        mvc.perform(post("/api/reviews/assignments/3/report")
                        .with(user(principal(202L, "REVIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validReport()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    private String validReport() {
        return """
                {"scoreNovelty":8,"scoreQuality":7,"scoreClarity":9,
                 "commentsToEditor":"建议小修","commentsToAuthor":"请补充消融实验", "recommendation":"MINOR"}
                """;
    }

    private UserDetailsImpl principal(Long id, String role) {
        return UserDetailsImpl.of(id, "test-" + id, "password", 1, java.util.Set.of(role));
    }
}
