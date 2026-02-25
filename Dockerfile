# =========================
# 1) Build stage
# =========================
FROM gradle:8.8-jdk17 AS build
WORKDIR /app

# 캐시 최적화:
# Gradle 설정/스크립트가 바뀌지 않으면 의존성 다운로드 레이어를 재사용할 수 있음.
COPY build.gradle* settings.gradle* gradle.properties* /app/
COPY gradle /app/gradle

# 의존성 캐시(실패해도 전체 빌드를 막지 않도록 || true)
RUN gradle --no-daemon dependencies || true

# 소스 복사
COPY . /app

# 테스트는 CI에서 수행하고, 이미지 빌드에서는 bootJar만 만듦.
RUN ./gradlew bootJar -x test --no-daemon

# =========================
# 2) Run stage
# =========================
FROM eclipse-temurin:17-jre
WORKDIR /app

# healthcheck.sh가 컨테이너 안에서 curl을 쓰므로 설치해둠
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# build stage에서 만들어진 jar를 런타임 이미지로 복사
COPY --from=build /app/build/libs/*.jar app.jar

# JVM 옵션은 런타임에서 주입 가능하도록 비워둠(Compose/EC2에서 환경변수로 주입)
ENV JAVA_OPTS=""

# app:8080 (서비스 포트), app:8081 (관리 포트, actuator)
EXPOSE 8080 8081

# sh -c 를 쓰는 이유: JAVA_OPTS 같은 문자열 환경변수를 자연스럽게 확장하기 위함
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
