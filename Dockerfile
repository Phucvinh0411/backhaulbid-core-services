# ============================================
# Multi-stage Dockerfile for Spring Boot
# Multi-module Maven Project
# ============================================
# Usage: docker build --build-arg MODULE=identity-service -t backhaulbid-identity .

# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache Maven dependencies
COPY pom.xml ./
COPY identity-service/pom.xml ./identity-service/
COPY fleet-service/pom.xml ./fleet-service/
COPY wallet-service/pom.xml ./wallet-service/
COPY contract-service/pom.xml ./contract-service/
COPY media-service/pom.xml ./media-service/
# COPY notification-service/pom.xml ./notification-service/

RUN mvn dependency:go-offline -B --no-transfer-progress

# Copy source code and build
COPY . .

ARG MODULE=identity-service
RUN mvn clean package -pl ${MODULE} -am -DskipTests -B --no-transfer-progress

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

RUN addgroup --system --gid 1001 spring
RUN adduser --system --uid 1001 spring

ARG MODULE=identity-service
COPY --from=builder --chown=spring:spring /app/${MODULE}/target/*.jar app.jar

RUN mkdir -p /data/fleet-documents /data/business-verifications && chown -R spring:spring /data && chmod -R 777 /data

COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
