package com.journalflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class InteractionDtos {

    @Data
    public static class CommentRequest {
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 2000, message = "评论最长 2000 字")
        private String content;
        @NotNull(message = "目标稿件不能为空")
        private Long manuscriptId;
        /** 回复的顶级评论 ID（二级回复） */
        private Long parentId;
    }

    @Data
    public static class FavoriteRequest {
        @NotNull(message = "稿件 ID 不能为空")
        private Long manuscriptId;
    }

    @Data
    public static class SubscriptionRequest {
        private Long categoryId;
        private String keyword;
    }
}
