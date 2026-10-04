package com.journalflow.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在 Controller 方法上，AOP 切面统一写入 operation_log。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    /** 动作，如 SUBMIT_MANUSCRIPT */
    String action();
    /** 操作对象类型，如 MANUSCRIPT */
    String objectType() default "";
    /** 对象 ID 的参数名（从方法参数中按名解析，支持占位符 {id}） */
    String objectIdParam() default "";
}
