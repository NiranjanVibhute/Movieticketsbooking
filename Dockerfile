# ==============================================================================
# Production-Ready Multi-Stage Dockerfile for Dark-Ops Cinema Web Application
# Compatible with Render, Railway, Fly.io, AWS ECS, GCP Cloud Run, and Docker
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build & Dependency Resolution
# ------------------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# Cache Maven dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy project source code and resources
COPY src ./src
COPY web ./web

# Compile and package fat JAR with all bundled dependencies (SQLite JDBC, SLF4J)
RUN mvn clean package -DskipTests

# ------------------------------------------------------------------------------
# Stage 2: Production Runtime Environment
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre

# Set environment metadata
LABEL maintainer="Dark-Ops Cinema Engineering"
LABEL description="Full-stack Java Movie Ticket Booking Platform"

WORKDIR /app

# Install sqlite3 package for optional CLI inspections & health checks
RUN apt-get update && apt-get install -y --no-install-recommends sqlite3 curl \
    && rm -rf /var/lib/apt/lists/*

# Create application user for security
RUN groupadd -r appuser && useradd -r -g appuser -d /app appuser

# Copy compiled application JAR from builder stage
COPY --from=builder /build/target/movie-ticket-booking-1.0.0.jar /app/app.jar

# Copy static web assets and database schema
COPY --from=builder /build/web /app/web
COPY --from=builder /build/src/main/resources/schema.sql /app/schema.sql

# Pre-initialize SQLite database and ensure file permissions
RUN sqlite3 /app/movietickets.db < /app/schema.sql \
    && chown -R appuser:appuser /app \
    && chmod -R 775 /app

# Switch to non-privileged user
USER appuser

# Default port configuration (Render automatically injects $PORT at runtime)
ENV PORT=8081
EXPOSE 8081

# Healthcheck to ensure container availability
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8081}/api/movies || exit 1

# Execute the standalone Java AppServer
ENTRYPOINT ["sh", "-c", "java -Dfile.encoding=UTF-8 -cp /app/app.jar com.movietickets.AppServer"]
