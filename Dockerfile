# ---------------------------------------------------------------------------
# Build stage
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Dependencies resolve in their own layer, keyed only on the POM. Source edits -- which is
# to say almost every rebuild -- then reuse the cached Maven repository instead of
# re-downloading Spring Boot from scratch.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# Split the fat jar into layers ordered by how often they change. Dependencies (~60 MB,
# changes monthly) land in one layer, application classes (~200 KB, changes hourly) in
# another, so a code-only deploy pushes and pulls kilobytes rather than the whole image.
RUN java -Djarmode=tools -jar target/auth-service-*.jar extract --layers --launcher --destination extracted

# ---------------------------------------------------------------------------
# Runtime stage
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# A JRE, not a JDK: no compiler, no jstack, no jar tool for an intruder to reach for.
RUN addgroup -S -g 1001 tenantbase && adduser -S -u 1001 -G tenantbase tenantbase

WORKDIR /app

# Copied in the layers' own order, most stable first.
COPY --from=build --chown=tenantbase:tenantbase /build/extracted/dependencies/ ./
COPY --from=build --chown=tenantbase:tenantbase /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=tenantbase:tenantbase /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=tenantbase:tenantbase /build/extracted/application/ ./

USER tenantbase

EXPOSE 8081

# MaxRAMPercentage, not -Xmx: the JVM sizes its heap from the container's cgroup limit, so
# raising the limit in compose or Kubernetes is enough and there is no hard-coded number to
# forget. ExitOnOutOfMemoryError makes an OOM a restart the orchestrator can see rather than
# a process that stays up serving errors.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD wget -q --spider http://localhost:8081/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
