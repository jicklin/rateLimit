package com.marry.starter.ratelimit.annotation;

import java.lang.annotation.*;

/**
 * 防重复提交参数排除注解
 * 标记在方法参数上，表示该参数不参与防重复提交key的生成
 * 这是一个便捷注解，等同于 @DuplicateSubmitParam(include = false)
 * 
 * @author marry
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DuplicateSubmitIgnore {
    
    /**
     * 忽略原因说明（可选）
     */
    String reason() default "";
}
