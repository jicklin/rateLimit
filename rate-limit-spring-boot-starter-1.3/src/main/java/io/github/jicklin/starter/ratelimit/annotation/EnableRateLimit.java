package io.github.jicklin.starter.ratelimit.annotation;

import io.github.jicklin.starter.ratelimit.autoconfigure.RateLimitAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用限流功能注解
 *
 * 在Spring Boot主类或配置类上添加此注解来启用限流功能。
 * 具体的配置参数请通过application.yml配置文件进行设置。
 *
 * 示例:
 * <pre>
 * &#64;SpringBootApplication
 * &#64;EnableRateLimit
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * </pre>
 *
 * 配置示例:
 * <pre>
 * rate-limit:
 *   enabled: true
 *   interceptor:
 *     enabled: true
 *     exclude-path-patterns:
 *       - "/static/**"
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(RateLimitAutoConfiguration.class)
public @interface EnableRateLimit {
}
