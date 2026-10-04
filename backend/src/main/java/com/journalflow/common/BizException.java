package com.journalflow.common;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BizException notFound(String msg) {
        return new BizException(ErrorCode.NOT_FOUND, msg);
    }

    public static BizException forbidden(String msg) {
        return new BizException(ErrorCode.FORBIDDEN, msg);
    }

    public static BizException conflict(String msg) {
        return new BizException(ErrorCode.CONFLICT, msg);
    }

    public static BizException badParam(String msg) {
        return new BizException(ErrorCode.BAD_PARAM, msg);
    }
}
