#!/bin/bash

# 限流Starter构建脚本

echo "=========================================="
echo "开始构建限流Spring Boot Starter"
echo "=========================================="

# 检查Maven是否安装
if ! command -v mvn &> /dev/null; then
    echo "错误: Maven未安装或不在PATH中"
    exit 1
fi

# 进入starter目录
cd rate-limit-spring-boot-starter

echo "清理之前的构建..."
mvn clean

echo "编译和打包starter..."
mvn compile package -DskipTests

if [ $? -eq 0 ]; then
    echo "✅ 编译成功"
else
    echo "❌ 编译失败"
    exit 1
fi

echo "运行测试..."
mvn test

if [ $? -eq 0 ]; then
    echo "✅ 测试通过"
else
    echo "⚠️  测试失败，但继续安装"
fi

echo "安装到本地Maven仓库..."
mvn install -DskipTests

if [ $? -eq 0 ]; then
    echo "✅ 安装成功"
else
    echo "❌ 安装失败"
    exit 1
fi

# 返回根目录
cd ..

echo "=========================================="
echo "Starter构建完成！"
echo "=========================================="
echo ""
echo "现在可以在主项目中使用starter了："
echo "1. 确保pom.xml中已添加starter依赖"
echo "2. 在主类上添加@EnableRateLimit注解"
echo "3. 配置application.yml中的限流参数（可选）"
echo "4. 启动应用测试限流功能"
echo ""
echo "使用示例："
echo "  @SpringBootApplication"
echo "  @EnableRateLimit"
echo "  public class Application {"
echo "      public static void main(String[] args) {"
echo "          SpringApplication.run(Application.class, args);"
echo "      }"
echo "  }"
echo ""
echo "测试命令："
echo "  mvn spring-boot:run"
echo "  curl -X POST http://localhost:8080/starter-example/rules"
echo "  curl http://localhost:8080/starter-example/api/test"
echo ""
echo "查看更多使用说明: cat STARTER_USAGE.md"
