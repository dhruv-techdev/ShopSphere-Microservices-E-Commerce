# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build

# Layer 1: parent POM — install without submodule recursion
COPY pom.xml /workspace/pom.xml
RUN mvn -N -f /workspace/pom.xml install

# Layer 2: shared libraries — install into local repo
COPY common-lib /workspace/common-lib
RUN mvn -f /workspace/common-lib/pom.xml install -DskipTests
COPY security-lib /workspace/security-lib
RUN mvn -f /workspace/security-lib/pom.xml install -DskipTests

# Layer 3: target service
ARG SERVICE_DIR
COPY ${SERVICE_DIR} /workspace/${SERVICE_DIR}
RUN mvn -f /workspace/${SERVICE_DIR}/pom.xml package -DskipTests

# Runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG SERVICE_DIR
COPY --from=build /workspace/${SERVICE_DIR}/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
