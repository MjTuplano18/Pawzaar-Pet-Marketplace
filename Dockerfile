# syntax=docker/dockerfile:1

# ── Stage 1: build the jar ─────────────────────────────────────────────────────
# A full JDK is only needed to COMPILE. It never ships in the final image.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy only the files needed to resolve dependencies first. Docker caches layers, so
# the (slow) dependency download only re-runs when pom.xml changes, not on every code edit.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline

# Now bring in the source and build the executable jar.
COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package

# ── Stage 2: run it ───────────────────────────────────────────────────────────
# A JRE (not a JDK) on Alpine: much smaller attack surface and image size.
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Never run the app as root: if it is ever compromised, the blast radius is smaller.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# COPY --from=build takes the jar out of the build stage; the JDK and the ~/.m2 cache
# stay behind. Only this one file crosses over.
COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080

# D3: container-aware heap sizing. Modern JVMs read the cgroup memory limit by default
# (UseContainerSupport), so a percentage adapts to whatever memory the platform grants — a fixed
# -Xmx would either waste RAM or exceed the limit and get the process OOM-killed. ExitOnOutOfMemoryError
# makes a heap blow-up fail fast (so the orchestrator can restart) instead of hanging half-alive.
#
# exec form (JSON array): java becomes PID 1 and receives OS signals directly, so `docker stop`
# delivers SIGTERM straight to the JVM — which, combined with server.shutdown=graceful, lets
# in-flight requests finish instead of being cut off.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:InitialRAMPercentage=50.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-jar", "/app/app.jar"]

# D3: Docker-level health check against Actuator. busybox (and its `wget`) ships with Alpine, so no
# extra package is needed. /actuator/health answers 503 when a dependency such as the database is
# down, which flips the container to "unhealthy" so a platform can restart it or stop routing traffic.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

