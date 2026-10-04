package com.journalflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.journalflow.common.BizException;
import com.journalflow.dto.AuthDtos.*;
import com.journalflow.entity.Role;
import com.journalflow.entity.User;
import com.journalflow.entity.UserRole;
import com.journalflow.mapper.RoleMapper;
import com.journalflow.mapper.UserMapper;
import com.journalflow.mapper.UserRoleMapper;
import com.journalflow.security.JwtService;
import com.journalflow.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginVO login(LoginRequest req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw BizException.badParam("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(com.journalflow.common.ErrorCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }
        List<String> roles = userMapper.selectRoleCodes(user.getId());
        String token = jwtService.createToken(user.getId(), user.getUsername(), roles);
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUser(toVO(user, roles));
        return vo;
    }

    @Transactional
    public UserVO register(RegisterRequest req) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (exists > 0) {
            throw BizException.badParam("用户名已被占用");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRealName(req.getRealName());
        user.setEmail(req.getEmail());
        user.setAffiliation(req.getAffiliation());
        user.setResearchFields(req.getResearchFields());
        user.setStatus(1);
        userMapper.insert(user);
        grantRole(user.getId(), "AUTHOR");
        return toVO(user, List.of("AUTHOR"));
    }

    @Transactional
    public void grantRole(Long userId, String roleCode) {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode));
        if (role == null) {
            throw new BizException(com.journalflow.common.ErrorCode.INTERNAL, "角色不存在：" + roleCode);
        }
        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleId(role.getId());
        userRoleMapper.insert(ur);
    }

    public UserVO me(UserDetailsImpl current) {
        User user = userMapper.selectById(current.getId());
        return toVO(user, userMapper.selectRoleCodes(user.getId()));
    }

    public UserVO updateProfile(UserDetailsImpl current, ProfileUpdateRequest req) {
        User user = userMapper.selectById(current.getId());
        if (req.getRealName() != null) user.setRealName(req.getRealName());
        if (req.getEmail() != null) user.setEmail(req.getEmail());
        if (req.getAffiliation() != null) user.setAffiliation(req.getAffiliation());
        if (req.getResearchFields() != null) user.setResearchFields(req.getResearchFields());
        userMapper.updateById(user);
        return toVO(user, userMapper.selectRoleCodes(user.getId()));
    }

    public void changePassword(UserDetailsImpl current, PasswordChangeRequest req) {
        User user = userMapper.selectById(current.getId());
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw BizException.badParam("原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
    }

    public UserVO toVO(User user, List<String> roles) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setAffiliation(user.getAffiliation());
        vo.setResearchFields(user.getResearchFields());
        vo.setRoles(roles);
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
