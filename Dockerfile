FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven files
COPY pom.xml .
COPY src ./src

# Build application
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests && \
    apk del maven

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy built JAR
COPY --from=builder /app/target/*.jar app.jar

# Expose ports
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
