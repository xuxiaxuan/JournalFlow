package com.journalflow.controller;

import com.journalflow.common.OperationLog;
import com.journalflow.common.Result;
import com.journalflow.common.SecurityUtils;
import com.journalflow.dto.AuthDtos.LoginRequest;
import com.journalflow.dto.AuthDtos.LoginVO;
import com.journalflow.dto.AuthDtos.PasswordChangeRequest;
import com.journalflow.dto.AuthDtos.ProfileUpdateRequest;
import com.journalflow.dto.AuthDtos.RegisterRequest;
import com.journalflow.dto.AuthDtos.UserVO;
import com.journalflow.service.AuthService;
import com.journalflow.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final NotificationService notificationService;

    @PostMapping("/login")
    @OperationLog(action = "LOGIN", objectType = "USER")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/register")
    @OperationLog(action = "REGISTER", objectType = "USER")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    @PostMapping("/logout")
    @OperationLog(action = "LOGOUT", objectType = "USER")
    public Result<Void> logout() {
        // JWT 无状态：前端清除 Token 即可；此处留痕
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(@AuthenticationPrincipal UserDetails user) {
        UserVO vo = authService.me((com.journalflow.security.UserDetailsImpl) user);
        return Result.ok(Map.of("user", vo, "unreadCount", notificationService.unreadCount(vo.getId())));
    }

    @PutMapping("/profile")
    @OperationLog(action = "UPDATE_PROFILE", objectType = "USER")
    public Result<UserVO> updateProfile(@AuthenticationPrincipal UserDetails user,
                                        @Valid @RequestBody ProfileUpdateRequest req) {
        return Result.ok(authService.updateProfile((com.journalflow.security.UserDetailsImpl) user, req));
    }

    @PutMapping("/password")
    @OperationLog(action = "CHANGE_PASSWORD", objectType = "USER")
    public Result<Void> changePassword(@AuthenticationPrincipal UserDetails user,
                                       @Valid @RequestBody PasswordChangeRequest req) {
        authService.changePassword((com.journalflow.security.UserDetailsImpl) user, req);
        return Result.ok();
    }
}
