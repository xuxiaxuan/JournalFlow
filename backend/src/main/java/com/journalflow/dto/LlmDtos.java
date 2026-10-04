package com.journalflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class LlmDtos {

    @Data
    public static class CreateTaskRequest {
        /** EXTRACT/SUMMARY/EXPLAIN */
        @NotBlank(message = "场景不能为空")
        private String scene;
        @NotNull(message = "稿件 ID 不能为空")
        private Long manuscriptId;
    }

    @Data
    public static class ConfirmRequest {
        /** 人工确认/修订后的最终 JSON */
        @NotBlank(message = "确认内容不能为空")
        private String confirmedOutput;
    }
}
