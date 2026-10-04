package com.journalflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class ReviewDtos {

    @Data
    public static class AssignReviewersRequest {
        @NotNull(message = "审稿人列表不能为空")
        @Size(min = 1, max = 5, message = "邀请 1-5 名审稿人")
        private List<Long> reviewerIds;
        private LocalDateTime dueAt;
    }

    @Data
    public static class DeclineRequest {
        private String reason;
    }

    @Data
    public static class ReportRequest {
        @NotNull(message = "创新性评分不能为空")
        @Min(value = 1, message = "评分范围 1-10")
        @Max(value = 10, message = "评分范围 1-10")
        private Integer scoreNovelty;
        @NotNull(message = "学术质量评分不能为空")
        @Min(1) @Max(10)
        private Integer scoreQuality;
        @NotNull(message = "表达清晰度评分不能为空")
        @Min(1) @Max(10)
        private Integer scoreClarity;
        private String commentsToEditor;
        @NotBlank(message = "给作者的意见不能为空")
        private String commentsToAuthor;
        @NotBlank(message = "建议等级不能为空")
        private String recommendation; // ACCEPT/MINOR/MAJOR/REJECT
    }
}
