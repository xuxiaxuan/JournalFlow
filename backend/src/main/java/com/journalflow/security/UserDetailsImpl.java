package com.journalflow.security;

import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Data
public class UserDetailsImpl implements UserDetails {
    private Long id;
    private String username;
    private String passwordHash;
    private Integer status;
    private Set<String> roleCodes;
    private Collection<? extends GrantedAuthority> authorities;

    public static UserDetailsImpl of(Long id, String username, String passwordHash, Integer status, Set<String> roleCodes) {
        UserDetailsImpl u = new UserDetailsImpl();
        u.id = id;
        u.username = username;
        u.passwordHash = passwordHash;
        u.status = status;
        u.roleCodes = roleCodes;
        u.authorities = roleCodes.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        return u;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != null && status == 1;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
