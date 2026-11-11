
# ==========================================
# Stage 1: Build application
# ==========================================
FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy parent Maven configuration
COPY pom.xml .

# Copy module Maven configurations
COPY ride-contracts/pom.xml ride-contracts/pom.xml
COPY ride-service/pom.xml ride-service/pom.xml

# Copy source code
COPY ride-contracts/src ride-contracts/src
COPY ride-service/src ride-service/src

# Build Ride Service and its dependencies
RUN mvn -B -pl ride-service -am clean package -DskipTests

#DskipTests skips tests inside the Docker image build. We already ran your tests successfully with Maven separately.
#We'll continue running tests before deployment.

# ==========================================
# Stage 2: Run application
# ==========================================
FROM eclipse-temurin:21-jre

# Install curl for Docker health checks
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy executable Spring Boot JAR
COPY --from=build /workspace/ride-service/target/ride-service-1.0.0-SNAPSHOT.jar app.jar

# Application port
EXPOSE 8081

# Start Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
