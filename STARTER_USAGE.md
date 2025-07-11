# 限流Starter使用指南

本文档介绍如何在现有项目中使用新创建的限流starter模块。

## 项目结构

```
rateLimit/
├── rate-limit-spring-boot-starter/     # 新创建的starter模块
│   ├── src/main/java/
│   │   └── com/marry/ratelimit/starter/
│   │       ├── autoconfigure/           # 自动配置类
│   │       ├── interceptor/             # 拦截器
│   │       ├── model/                   # 数据模型
│   │       ├── service/                 # 服务接口和实现
│   │       ├── strategy/                # 限流策略
│   │       └── util/                    # 工具类
│   ├── src/main/resources/
│   │   └── META-INF/
│   │       └── spring.factories         # 自动配置声明
│   ├── pom.xml                          # starter的Maven配置
│   └── README.md                        # starter使用文档
├── src/main/java/                       # 原项目代码
├── application-starter.yml              # 使用starter的配置示例
├── pom.xml                              # 已更新，添加了starter依赖
└── STARTER_USAGE.md                     # 本文档
```

## 使用步骤

### 1. 构建Starter模块

首先需要构建starter模块：

```bash
cd rate-limit-spring-boot-starter
mvn clean install
```

这会将starter安装到本地Maven仓库。

### 2. 更新主项目依赖

主项目的`pom.xml`已经更新，添加了starter依赖：

```xml
<dependency>
    <groupId>com.marry</groupId>
    <artifactId>rate-limit-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 3. 启用限流功能

在Spring Boot主类上添加`@EnableRateLimit`注解：

```java
@SpringBootApplication
@EnableRateLimit
public class RatelimitApplication {
    public static void main(String[] args) {
        SpringApplication.run(RatelimitApplication.class, args);
    }
}
```

### 4. 配置应用（可选）

使用`application-starter.yml`中的配置，或者在现有的配置文件中添加：

```yaml
rate-limit:
  interceptor:
    enabled: true
    exclude-path-patterns:
      - "/ratelimit/**"
      - "/static/**"
```

### 5. 启动应用

启动应用后，限流功能会自动生效。

## 测试验证

### 1. 创建限流规则

```bash
curl -X POST http://localhost:8080/starter-example/rules
```

### 2. 测试限流效果

```bash
# 快速发送多个请求测试限流
for i in {1..20}; do
  curl http://localhost:8080/starter-example/api/test
  echo ""
done
```

### 3. 查看统计信息

```bash
curl http://localhost:8080/starter-example/stats
```

## @EnableRateLimit注解说明

`@EnableRateLimit`注解用于启用限流功能，具体的配置参数通过`application.yml`配置文件进行设置。

### 使用示例

```java
// 基本使用
@SpringBootApplication
@EnableRateLimit
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

// 在配置类上使用
@Configuration
@EnableRateLimit
public class RateLimitConfig {
}
```

## 与原项目的对比

### 原项目方式

原项目中需要：
1. 手动配置拦截器
2. 手动注册各种服务Bean
3. 手动配置Redis模板
4. 复制大量代码文件
5. 通过spring.factories自动配置

### 使用Starter方式

使用starter后：
1. 通过`@EnableRateLimit`注解显式启用
2. 自动配置所有必要的Bean
3. 只需要添加依赖和注解
4. 开箱即用，无需复制代码
5. 可以在多个项目中复用
6. 更明确的控制和配置

## 迁移指南

如果要将现有项目完全迁移到使用starter：

### 1. 删除不需要的文件

可以删除以下原项目中的文件（因为starter已包含）：
- `src/main/java/com/marry/ratelimit/config/WebConfig.java`
- `src/main/java/com/marry/ratelimit/config/RateLimitConfig.java`
- `src/main/java/com/marry/ratelimit/interceptor/RateLimitInterceptor.java`
- `src/main/java/com/marry/ratelimit/service/` 下的所有服务类
- `src/main/java/com/marry/ratelimit/strategy/` 下的所有策略类
- `src/main/java/com/marry/ratelimit/util/` 下的工具类

### 2. 更新导入语句

将原来的导入语句：
```java
import com.marry.ratelimit.model.RateLimitRule;
import com.marry.ratelimit.service.RateLimitConfigService;
```

改为：

```java


```

### 3. 保留的文件

可以保留以下文件（这些是业务相关的）：
- `src/main/java/com/marry/ratelimit/controller/RateLimitController.java` （管理界面控制器）
- `src/main/java/com/marry/ratelimit/config/DataInitializer.java` （数据初始化）
- `src/main/resources/webapp/` 下的模板文件
- `src/main/resources/static/` 下的静态资源

## 优势

### 1. 代码复用
- starter可以在多个项目中使用
- 避免重复编写相同的限流逻辑

### 2. 维护性
- 限流核心逻辑集中在starter中
- 修复bug或添加功能只需要更新starter版本

### 3. 易用性
- 开箱即用，无需复杂配置
- 遵循Spring Boot的约定优于配置原则

### 4. 扩展性
- 可以通过实现接口来扩展功能
- 支持自定义限流策略

## 注意事项

1. **版本管理**: 确保starter版本与项目兼容
2. **配置冲突**: 避免与原有配置产生冲突
3. **依赖管理**: 注意Redis等依赖的版本兼容性
4. **测试验证**: 充分测试限流功能是否正常工作

## 下一步

1. 可以将starter发布到私有Maven仓库
2. 为starter添加更多的配置选项
3. 添加更多的限流策略实现
4. 完善文档和示例代码
