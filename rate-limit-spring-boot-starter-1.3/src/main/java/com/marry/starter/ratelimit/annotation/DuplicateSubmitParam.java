package com.marry.starter.ratelimit.annotation;

import java.lang.annotation.*;

/**
 * 防重复提交参数注解
 * 标记在方法参数上，用于控制该参数是否参与防重复提交key的生成
 * 支持分组功能，不同分组的参数会生成独立的防重复key
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

    /**
     * 参数分组，用于将参数分组进行独立的防重复校验
     * 相同分组的参数会组合生成一个防重复key
     * 不同分组会生成不同的key，进行独立校验
     * 默认为空字符串，表示使用全局分组
     */
    String group() default "";

    /**
     * 分组权重，用于控制分组的优先级
     * 权重越高的分组越优先进行校验
     * 默认为0，表示普通优先级
     */
    int groupWeight() default 0;

    /**
     * 参数值处理器名称
     * 用于指定如何处理参数值
     * 默认为"default"，表示使用默认处理器
     *
     * 内置处理器：
     * - "default": 默认处理器，直接返回原始值
     * - "hash": 哈希处理器，将值转换为MD5哈希
     * - "mask": 掩码处理器，对敏感信息进行掩码
     * - "normalize": 标准化处理器，去空格、转小写等
     *
     * 也可以指定自定义处理器的名称
     */
    String processor() default "default";

    /**
     * 处理器参数
     * 传递给处理器的额外参数，格式为key=value，多个参数用逗号分隔
     * 例如：length=10,prefix=USER
     */
    String processorParams() default "";
}
