FROM gradle:8.14.3-jdk21 AS builder
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
RUN useradd --system --uid 10001 --create-home melody
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
USER melody
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
