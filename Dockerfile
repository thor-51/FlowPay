# ---- Build stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Leverage Docker layer caching: resolve dependencies before copying source
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Run as non-root for defense in depth
RUN groupadd -r tps && useradd -r -g tps tps
RUN mkdir -p /app/logs && chown -R tps:tps /app

COPY --from=build /build/target/transaction-processing-system.jar /app/app.jar

# Optional New Relic agent mount point (see docs/NEW_RELIC.md).
# If NEW_RELIC_ENABLED=true, mount the agent jar at /app/newrelic/newrelic.jar and pass
# JAVA_OPTS="-javaagent:/app/newrelic/newrelic.jar" — the ENTRYPOINT below expands $JAVA_OPTS
# onto the java command line, so this is the one place that needs to change to attach it.
ENV JAVA_OPTS="" \
    SPRING_PROFILES_ACTIVE=dev

USER tps
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
