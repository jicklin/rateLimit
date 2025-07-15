package com.marry.starter.ratelimit.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交注解
 * 
 * @author marry
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PreventDuplicateSubmit {

    /**
     * 防重复提交的时间间隔，默认5秒
     */
    long interval() default 5;

    /**
     * 时间单位，默认秒
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 提示信息
     */
    String message() default "请勿重复提交";

    /**
     * 参数处理策略
     * INCLUDE_ALL: 包含所有参数（默认）
     * INCLUDE_ANNOTATED: 只包含被@DuplicateSubmitParam标注的参数
     * EXCLUDE_ANNOTATED: 排除被@DuplicateSubmitIgnore标注的参数
     * EXCLUDE_ALL: 不包含任何参数
     */
    ParamStrategy paramStrategy() default ParamStrategy.INCLUDE_ALL;

    /**
     * 是否包含用户标识在key中，默认true
     * true: 不同用户的请求不会互相影响
     * false: 所有用户共享防重复提交限制
     */
    boolean includeUser() default true;

    /**
     * 自定义key的前缀，默认为空
     */
    String keyPrefix() default "";

    /**
     * 参数处理策略枚举
     */
    enum ParamStrategy {
        /**
         * 包含所有参数（排除HttpServletRequest等特殊参数）
         */
        INCLUDE_ALL,

        /**
         * 只包含被@DuplicateSubmitParam(include=true)标注的参数
         */
        INCLUDE_ANNOTATED,

        /**
         * 排除被@DuplicateSubmitIgnore标注的参数，包含其他所有参数
         */
        EXCLUDE_ANNOTATED,

        /**
         * 不包含任何参数，只基于方法和用户
         */
        EXCLUDE_ALL
    }
}
