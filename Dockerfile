FROM gradle:8.5-jdk21 AS build
WORKDIR /app

ARG GITHUB_ACTOR
ARG PAT_TOKEN

ENV GITHUB_ACTOR=${GITHUB_ACTOR}
ENV PAT_TOKEN=${PAT_TOKEN}

COPY build.gradle.kts gradlew gradlew.bat ./
COPY gradle ./gradle
COPY gradle gradle

RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon --stacktrace

COPY src ./src

RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=8081"]