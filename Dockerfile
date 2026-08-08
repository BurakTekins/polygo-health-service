# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests clean package
RUN JAR_PATH="$(find . -type f -path '*/target/*.jar' -name '*.jar' -print -quit)"; cp "$JAR_PATH" /tmp/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /tmp/app.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
USER 1001
ENTRYPOINT ["java","-jar","/app/app.jar"]