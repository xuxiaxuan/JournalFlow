package com.journalflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

public class ManuscriptDtos {

    @Data
    public static class SaveRequest {
        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题最长 200 字")
        private String title;
        private String abstract_;
        @Size(max = 500, message = "关键词最长 500 字")
        private String keywords;
        @NotNull(message = "学科分类不能为空")
        private Long categoryId;
        private Boolean submitterCorresponding = true;
        @Size(max = 30, message = "标签最多 30 个")
        private List<Long> tagIds;
        /** 合作者（不含投稿人本人）：userId 为注册用户，name/affiliation 为快照 */
        @jakarta.validation.Valid
        @Size(max = 30, message = "共同作者最多 30 人")
        private List<@NotNull CoAuthor> coAuthors;
    }

    @Data
    public static class CoAuthor {
        private Long userId;
        @Size(max = 100, message = "作者姓名最长 100 字")
        private String name;
        @Size(max = 200, message = "作者单位最长 200 字")
        private String affiliation;
        private Boolean corresponding;
    }

    @Data
    public static class PublishRequest {
        @NotBlank(message = "卷号不能为空")
        @Size(max = 20, message = "卷号最长 20 字")
        private String volume;
        @NotBlank(message = "期号不能为空")
        @Size(max = 20, message = "期号最长 20 字")
        private String issue;
        @NotBlank(message = "页码不能为空")
        @Size(max = 30, message = "页码最长 30 字")
        private String pages;
        @NotBlank(message = "DOI 不能为空")
        @Size(max = 100, message = "DOI 最长 100 字")
        @Pattern(regexp = "^10\\.\\d{4,9}/\\S+$", message = "DOI 格式不正确")
        private String doi;
    }

    @Data
    public static class CopyrightRequest {
        private String licenseType = "CC-BY-4.0";
        @NotBlank(message = "协议文本不能为空")
        private String agreementText;
    }
}
