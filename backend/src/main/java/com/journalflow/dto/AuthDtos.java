package com.journalflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class AuthDtos {

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度 3-50")
        private String username;
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 50, message = "密码长度 6-50")
        private String password;
        @NotBlank(message = "姓名不能为空")
        @Size(max = 100, message = "姓名最长 100 字")
        private String realName;
        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱最长 100 字")
        private String email;
        @Size(max = 200, message = "单位最长 200 字")
        private String affiliation;
        @Size(max = 500, message = "研究领域最长 500 字")
        private String researchFields;
    }

    @Data
    public static class ProfileUpdateRequest {
        @Size(max = 100, message = "姓名最长 100 字")
        private String realName;
        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱最长 100 字")
        private String email;
        @Size(max = 200, message = "单位最长 200 字")
        private String affiliation;
        @Size(max = 500, message = "研究领域最长 500 字")
        private String researchFields;
    }

    @Data
    public static class PasswordChangeRequest {
        @NotBlank(message = "原密码不能为空")
        private String oldPassword;
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 50, message = "新密码长度 6-50")
        private String newPassword;
    }

    @Data
    public static class UserVO {
        private Long id;
        private String username;
        private String realName;
        private String email;
        private String affiliation;
        private String researchFields;
        private List<String> roles;
        private LocalDateTime createdAt;
    }

    @Data
    public static class LoginVO {
        private String token;
        private UserVO user;
    }
}
