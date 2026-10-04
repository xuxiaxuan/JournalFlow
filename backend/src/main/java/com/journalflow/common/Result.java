package com.journalflow.common;

import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = ErrorCode.OK.getCode();
        r.message = "success";
        r.data = data;
        return r;
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> of(ErrorCode ec, String message) {
        Result<T> r = new Result<>();
        r.code = ec.getCode();
        r.message = message;
        return r;
    }
}
