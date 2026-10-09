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

# exec form (JSON array): java becomes PID 1 and receives OS signals directly, so
# `docker stop` shuts the app down gracefully.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
