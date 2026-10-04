package com.journalflow.service;

import com.journalflow.common.BizException;
import com.journalflow.common.ErrorCode;
import com.journalflow.dto.AuthDtos.LoginRequest;
import com.journalflow.dto.AuthDtos.PasswordChangeRequest;
import com.journalflow.dto.AuthDtos.RegisterRequest;
import com.journalflow.entity.Role;
import com.journalflow.entity.User;
import com.journalflow.mapper.RoleMapper;
import com.journalflow.mapper.UserMapper;
import com.journalflow.mapper.UserRoleMapper;
import com.journalflow.security.JwtService;
import com.journalflow.security.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceSecurityTest {
    @Mock UserMapper userMapper;
    @Mock RoleMapper roleMapper;
    @Mock UserRoleMapper userRoleMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @InjectMocks AuthService authService;

    @Test
    void invalidCredentialsUseGenericError() {
        when(userMapper.selectOne(any())).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> authService.login(loginRequest()));

        assertEquals(ErrorCode.BAD_PARAM, error.getErrorCode());
        assertEquals("用户名或密码错误", error.getMessage());
    }

    @Test
    void disabledAccountCannotLogin() {
        User user = user(101L, 0);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);

        BizException error = assertThrows(BizException.class,
                () -> authService.login(loginRequest()));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void successfulLoginCarriesDatabaseRolesIntoToken() {
        User user = user(101L, 1);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);
        when(userMapper.selectRoleCodes(101L)).thenReturn(List.of("AUTHOR", "REVIEWER"));
        when(jwtService.createToken(101L, "author1", List.of("AUTHOR", "REVIEWER"))).thenReturn("jwt");

        var result = authService.login(loginRequest());

        assertEquals("jwt", result.getToken());
        assertEquals(List.of("AUTHOR", "REVIEWER"), result.getUser().getRoles());
    }

    @Test
    void duplicateUsernameIsRejected() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        BizException error = assertThrows(BizException.class,
                () -> authService.register(registerRequest()));

        assertEquals(ErrorCode.BAD_PARAM, error.getErrorCode());
    }

    @Test
    void registrationHashesPasswordAndGrantsOnlyAuthorRole() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("123456")).thenReturn("new-hash");
        Role author = new Role();
        author.setId(2L);
        author.setCode("AUTHOR");
        when(roleMapper.selectOne(any())).thenReturn(author);

        var result = authService.register(registerRequest());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertEquals("new-hash", userCaptor.getValue().getPasswordHash());
        assertEquals(1, userCaptor.getValue().getStatus());
        assertEquals(List.of("AUTHOR"), result.getRoles());
        verify(userRoleMapper).insert(any());
    }

    @Test
    void passwordChangeRequiresCorrectOldPassword() {
        User user = user(101L, 1);
        when(userMapper.selectById(101L)).thenReturn(user);
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPassword("wrong");
        request.setNewPassword("new-password");

        BizException error = assertThrows(BizException.class,
                () -> authService.changePassword(principal(), request));

        assertEquals(ErrorCode.BAD_PARAM, error.getErrorCode());
    }

    @Test
    void successfulPasswordChangeStoresOnlyEncodedPassword() {
        User user = user(101L, 1);
        when(userMapper.selectById(101L)).thenReturn(user);
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPassword("123456");
        request.setNewPassword("new-password");

        authService.changePassword(principal(), request);

        assertEquals("new-hash", user.getPasswordHash());
        verify(userMapper).updateById(user);
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("author1");
        request.setPassword("123456");
        return request;
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newauthor");
        request.setPassword("123456");
        request.setRealName("新作者");
        request.setEmail("author@example.com");
        return request;
    }

    private User user(Long id, int status) {
        User user = new User();
        user.setId(id);
        user.setUsername("author1");
        user.setPasswordHash("encoded-password");
        user.setRealName("作者一");
        user.setStatus(status);
        return user;
    }

    private UserDetailsImpl principal() {
        return UserDetailsImpl.of(101L, "author1", "encoded-password", 1, java.util.Set.of("AUTHOR"));
    }
}
