# syntax=docker/dockerfile:1
# ↑ 声明使用最新 Dockerfile 语法，确保下面的 --mount=type=cache 可用

# ==================== 构建阶段 ====================
FROM maven:3.9.14-eclipse-temurin-17 AS build

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

# 从构建阶段复制构建的jar文件和src目录
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/src/ /app/src/

# 设置Java的Headless模式（key=value 写法）
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"

# 设置启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
