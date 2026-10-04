package com.journalflow.security;

import com.journalflow.entity.User;
import com.journalflow.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class DbUserDetailsService implements UserDetailsService {
    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return loadById(user);
    }

    public UserDetailsImpl loadById(User user) {
        var roleCodes = new HashSet<>(userMapper.selectRoleCodes(user.getId()));
        return UserDetailsImpl.of(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.getStatus(), roleCodes);
    }
}
