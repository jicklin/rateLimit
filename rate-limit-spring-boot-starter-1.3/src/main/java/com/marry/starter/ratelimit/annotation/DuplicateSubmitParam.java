package com.marry.starter.ratelimit.annotation;

import java.lang.annotation.*;

/**
 * 防重复提交参数注解
 * 标记在方法参数上，用于控制该参数是否参与防重复提交key的生成
 * 
 * @author marry
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DuplicateSubmitParam {

    /**
     * 是否包含此参数在key生成中
     * true: 包含此参数
     * false: 排除此参数
     */
    boolean include() default true;

    /**
     * 参数别名，用于key生成时的参数名
     * 如果为空，则使用参数的实际名称
     */
    String alias() default "";

    /**
     * 参数值提取路径，支持对象属性访问
     * 例如：user.id, request.orderId
     * 如果为空，则使用整个参数对象
     */
    String path() default "";
}
