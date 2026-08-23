FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY . .
RUN ./mvnw clean package -pl media-service -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/media-service/target/*.jar app.jar
COPY docker-entrypoint.sh .
RUN chmod +x docker-entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["./docker-entrypoint.sh"]
CMD ["java", "-jar", "app.jar"]
