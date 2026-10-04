package com.journalflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class AdminDtos {

    @Data
    public static class UserStatusRequest {
        @NotBlank(message = "状态不能为空")
        private String status; // "1" 正常 / "0" 禁用
    }

    @Data
    public static class UserRolesRequest {
        @NotBlank(message = "角色不能为空")
        private String roleCodes; // 逗号分隔：AUTHOR,REVIEWER
    }

    @Data
    public static class CategoryRequest {
        @NotBlank(message = "分类名不能为空")
        private String name;
        private Long parentId;
        private Integer sortNo;
    }

    @Data
    public static class UserCreateRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
        @NotBlank(message = "姓名不能为空")
        private String realName;
        private String email;
        private String affiliation;
        private String roleCodes; // 逗号分隔
    }
}
