package com.journalflow.common;

import lombok.Getter;

/**
 * 业务错误码分段：0 成功；401xx 未认证；403xx 越权；404xx 不存在；
 * 409xx 状态冲突；400xx 参数校验失败；500xx 服务端/依赖服务失败。
 */
@Getter
public enum ErrorCode {
    OK(0, 200, "成功"),
    UNAUTHORIZED(40101, 401, "未认证或登录已过期"),
    FORBIDDEN(40301, 403, "无权限执行该操作"),
    NOT_FOUND(40401, 404, "资源不存在"),
    CONFLICT(40901, 409, "状态冲突"),
    BAD_PARAM(40001, 400, "参数校验失败"),
    DEP_FAILED(50009, 502, "依赖服务调用失败"),
    INTERNAL(50000, 500, "系统内部错误");

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
