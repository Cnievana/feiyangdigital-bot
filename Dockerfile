# syntax=docker/dockerfile:1
# ↑ 声明使用最新 Dockerfile 语法，确保下面的 --mount=type=cache 可用

# ==================== 构建阶段 ====================
# --platform=$BUILDPLATFORM：用构建机自身架构跑 Maven。
# 本项目产物是纯 Java jar（与架构无关），这样可以「在 amd64 上构建一次、同时供 arm64 使用」，
# 避免 mvn 在 QEMU 模拟的 arm64 里跑 20 分钟。
FROM --platform=$BUILDPLATFORM maven:3.9.14-eclipse-temurin-17 AS build

WORKDIR /app

# 先只复制 pom.xml，把依赖单独预下载成一层：
# pom.xml 没改动时，这一层可以直接命中缓存，不必重新解析依赖。
# --mount=type=cache 让 ~/.m2 存进「构建缓存」而不是「镜像层」——
# 否则几百 MB 的依赖会被写进镜像层，每次构建都留一份，磁盘很快被撑满。
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline || true

# 再复制源码并打包（-DskipTests 跳过单元测试，加快构建）
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

# ==================== 运行阶段 ====================
# 使用 Eclipse Temurin 17 运行时镜像
FROM eclipse-temurin:17-jre-jammy

# 安装中文字体
RUN apt-get update \
 && apt-get install -y --no-install-recommends fonts-wqy-microhei \
 && rm -rf /var/lib/apt/lists/*

# 设置应用的目录结构
WORKDIR /app

# 从构建阶段复制 jar（资源文件已全部打进 jar，无需再额外拷贝 src 目录）
COPY --from=build /app/target/*.jar app.jar

# 设置Java的Headless模式（key=value 写法）
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"

# MaxRAMPercentage：让 JVM 按容器内存比例取堆（默认 25% 在 1C1G 小机器上偏小），
# 75% 是容器场景的常用取值；如需自定义可用 docker run -e JAVA_TOOL_OPTIONS="..." 覆盖
# 设置启动命令
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
