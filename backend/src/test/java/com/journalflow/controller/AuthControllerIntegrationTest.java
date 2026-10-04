package com.journalflow.controller;

import com.journalflow.common.GlobalExceptionHandler;
import com.journalflow.config.MybatisPlusConfig;
import com.journalflow.config.SecurityConfig;
import com.journalflow.dto.AuthDtos.LoginVO;
import com.journalflow.dto.AuthDtos.UserVO;
import com.journalflow.security.JwtAuthFilter;
import com.journalflow.security.UserDetailsImpl;
import com.journalflow.service.AuthService;
import com.journalflow.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AuthController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthFilter.class, MybatisPlusConfig.class, SecurityConfig.class}))
@ContextConfiguration(classes = {
        AuthController.class, GlobalExceptionHandler.class, AuthTestSecurityConfig.class
})
class AuthControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @MockBean AuthService authService;
    @MockBean NotificationService notificationService;

    @Test
    void anonymousLoginIsAllowedButValidated() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void validLoginReturnsTokenAndRoles() throws Exception {
        UserVO user = userVo();
        LoginVO login = new LoginVO();
        login.setToken("test.jwt.token");
        login.setUser(user);
        when(authService.login(any())).thenReturn(login);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"author1\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("test.jwt.token"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("AUTHOR"));
    }

    @Test
    void registrationRejectsInvalidEmailAndShortPassword() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"newuser","password":"123","realName":"新用户",
                                 "email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void currentUserEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void currentUserEndpointReturnsUnreadCount() throws Exception {
        UserDetailsImpl principal = principal();
        when(authService.me(principal)).thenReturn(userVo());
        when(notificationService.unreadCount(101L)).thenReturn(3L);

        mvc.perform(get("/api/auth/me").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("author1"))
                .andExpect(jsonPath("$.data.unreadCount").value(3));
    }

    @Test
    void profileUpdateValidatesEmail() throws Exception {
        mvc.perform(put("/api/auth/profile")
                        .with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrong-format\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordChangeRequiresMinimumLength() throws Exception {
        mvc.perform(put("/api/auth/password")
                        .with(user(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"123456\",\"newPassword\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    private UserVO userVo() {
        UserVO user = new UserVO();
        user.setId(101L);
        user.setUsername("author1");
        user.setRealName("作者一");
        user.setRoles(List.of("AUTHOR"));
        return user;
    }

    private UserDetailsImpl principal() {
        return UserDetailsImpl.of(101L, "author1", "password", 1, java.util.Set.of("AUTHOR"));
    }
}
