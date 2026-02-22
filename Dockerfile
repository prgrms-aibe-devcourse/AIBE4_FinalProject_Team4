# [Stage 1] Build: Gradle을 사용하여 JAR 파일 생성
FROM gradle:jdk17-noble AS builder
WORKDIR /build

# 의존성 캐싱을 위해 Gradle 파일만 먼저 복사
COPY build.gradle settings.gradle gradlew ./
COPY gradle gradle
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

# 소스 코드 복사 및 빌드
COPY . .
RUN ./gradlew bootJar --no-daemon

# [Stage 2] Run: 실행만을 위한 경량 JRE 환경
FROM azul/zulu-openjdk-alpine:17-jre
WORKDIR /app

# 빌드 스테이지에서 생성된 JAR 파일만 가져옴
COPY --from=builder /build/build/libs/*.jar app.jar

# 컨테이너로 실행 시 기본 프로파일을 prod로 강제
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]