package com.journalflow.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.journalflow.common.OperationLog;
import com.journalflow.common.SecurityUtils;
import com.journalflow.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作日志切面：拦截 @OperationLog 注解的方法，统一写入 operation_log。
 * 敏感字段（password 等）自动脱敏；文件参数只记录文件名。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog opLog) throws Throwable {
        Long userId = SecurityUtils.currentUserId();
        String ip = currentIp();
        Long objectId = resolveObjectId(pjp, opLog.objectIdParam());
        String detail = serializeArgs(pjp);
        boolean success = true;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            success = false;
            throw t;
        } finally {
            try {
                operationLogService.write(userId, opLog.action(), opLog.objectType(), objectId, detail, ip, success);
            } catch (Exception e) {
                log.warn("写操作日志失败: {}", e.getMessage());
            }
        }
    }

    private Long resolveObjectId(ProceedingJoinPoint pjp, String objectIdParam) {
        if (objectIdParam == null || objectIdParam.isBlank()) {
            return null;
        }
        String name = objectIdParam;
        if (name.startsWith("{") && name.endsWith("}")) {
            name = name.substring(1, name.length() - 1);
        }
        String[] names = ((MethodSignature) pjp.getSignature()).getParameterNames();
        Object[] args = pjp.getArgs();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                if (name.equals(names[i]) && args[i] instanceof Number n) {
                    return n.longValue();
                }
            }
        }
        return null;
    }

    private String serializeArgs(ProceedingJoinPoint pjp) {
        try {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            String[] names = sig.getParameterNames();
            Object[] args = pjp.getArgs();
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < args.length && names != null && i < names.length; i++) {
                Object value = args[i];
                if (value instanceof MultipartFile f) {
                    map.put(names[i], Map.of("fileName", f.getOriginalFilename(), "size", f.getSize()));
                } else if (value instanceof HttpServletRequest || value instanceof jakarta.servlet.http.HttpServletResponse) {
                    continue;
                } else if (value instanceof byte[]) {
                    map.put(names[i], "<bytes>");
                } else {
                    map.put(names[i], value);
                }
            }
            String json = objectMapper.writeValueAsString(map);
            // 敏感字段脱敏
            json = json.replaceAll("(\"(?:password|oldPassword|newPassword|apiKey|token)\"\\s*:\\s*\")[^\"]*\"", "$1***\"");
            return json.length() > 2000 ? json.substring(0, 2000) : json;
        } catch (Exception e) {
            return "<args>";
        }
    }

    private String currentIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest req = attrs.getRequest();
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = req.getRemoteAddr();
        }
        return ip;
    }
}
