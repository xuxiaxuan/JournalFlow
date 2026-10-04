package com.journalflow.common;

import com.journalflow.security.UserDetailsImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static UserDetailsImpl current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl ud) {
            return ud;
        }
        return null;
    }

    public static Long currentUserId() {
        UserDetailsImpl ud = current();
        return ud == null ? null : ud.getId();
    }

    public static boolean hasRole(String roleCode) {
        UserDetailsImpl ud = current();
        return ud != null && ud.getRoleCodes().contains(roleCode);
    }
}
