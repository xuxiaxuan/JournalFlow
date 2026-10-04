package com.journalflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

public class EditorDtos {

    @Data
    public static class FormatReviewRequest {
        @NotNull(message = "审查结论不能为空")
        private Boolean pass;
        private String reason;
    }

    @Data
    public static class DecisionRequest {
        /** ACCEPT/REVISE/REJECT */
        @NotBlank(message = "决定不能为空")
        private String decision;
        private String reason;
        /** 审稿综合意见（可来自 LLM 汇总任务人工确认结果） */
        private String summaryReport;
        private Long llmTaskId;
    }

    @Data
    public static class AppealHandleRequest {
        /** RESOLVED（申诉成立）/ DISMISSED（驳回申诉） */
        @NotBlank(message = "处理结果不能为空")
        private String status;
        @NotBlank(message = "终审说明不能为空")
        @Size(max = 2000, message = "终审说明最长 2000 字")
        private String result;
    }

    @Data
    public static class ReportHandleRequest {
        /** RESOLVED（处置成立）/ DISMISSED（驳回举报） */
        @NotBlank(message = "处理结果不能为空")
        private String status;
        @NotBlank(message = "处置说明不能为空")
        @Size(max = 2000, message = "处置说明最长 2000 字")
        private String resolution;
    }

    @Data
    public static class ReportCreateRequest {
        @NotBlank(message = "举报对象类型不能为空")
        private String targetType; // MANUSCRIPT/COMMENT/USER
        @NotNull(message = "举报对象 ID 不能为空")
        private Long targetId;
        @NotBlank(message = "举报原因不能为空")
        @Size(max = 30, message = "举报原因最长 30 字")
        private String reasonType;
        @NotBlank(message = "举报描述不能为空")
        @Size(max = 2000, message = "举报描述最长 2000 字")
        private String description;
    }

    @Data
    public static class AppealCreateRequest {
        private Long decisionId;
        private Long reportId;
        @NotBlank(message = "申诉内容不能为空")
        @Size(max = 2000, message = "申诉内容最长 2000 字")
        private String content;
    }

    @Data
    public static class AdminUserVO {
        private Long id;
        private String username;
        private String realName;
        private String email;
        private String affiliation;
        private String researchFields;
        private Integer status;
        private List<String> roles;
        private String createdAt;
    }
}
