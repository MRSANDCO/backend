# Use an official OpenJDK runtime as a parent image (Java 21)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

# Copy gradle files required for dependency resolution
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Download dependencies (this layer is cached unless build.gradle changes)
RUN ./gradlew dependencies --no-daemon

# Copy the actual source code
COPY src src

# Build the application skipping tests (since tests might require DB connections)
RUN ./gradlew build -x test --no-daemon

# Extract the jar file
RUN mkdir -p target/dependency && (cd target/dependency; jar -xf /workspace/app/build/libs/backend-0.0.1-SNAPSHOT.jar)

# Production Image - use a smaller JRE instead of full JDK
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Argument for port (defaults to 8080)
ARG PORT=8080
ENV PORT=${PORT}

# Expose the port
EXPOSE ${PORT}

# Copy the extracted application
COPY --from=build /workspace/app/target/dependency/BOOT-INF/lib /app/lib
COPY --from=build /workspace/app/target/dependency/META-INF /app/META-INF
COPY --from=build /workspace/app/target/dependency/BOOT-INF/classes /app

# Run the application
ENTRYPOINT ["java", "-Dserver.port=${PORT}", "-cp", "app:app/lib/*", "com.mrs.ca.backend.BackendApplication"]
