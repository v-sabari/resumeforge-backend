# ── Build stage ───────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# FIX-6: Copy pom.xml FIRST and pre-download all dependencies as a separate
# Docker layer.  When only source files change (not pom.xml), Docker reuses
# this cached layer and skips the ~200 MB download on every rebuild.
# Without this split, COPY src ./src invalidates the pom.xml layer, forcing
# a full dependency re-download on every deploy (~3-5 min on Render free tier).
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Source is copied after the dependency layer so code changes do not bust
# the dependency cache.
COPY src ./src

# FIX-6 (continued): -q suppresses Maven's verbose output in build logs.
RUN mvn clean package -DskipTests -q

# FIX-5: Verify both font files are inside the JAR at build time.
# If either font is missing (accidentally gitignored, deleted, or excluded),
# this RUN step fails the entire Docker build with a clear FATAL message.
# The bad image never gets pushed and the broken deploy never reaches Render.
# Without this check, a missing font only fails at runtime -> HTTP 500 in prod.
#
# Expected paths inside Spring Boot fat JAR:
#   BOOT-INF/classes/fonts/DejaVuSans.ttf
#   BOOT-INF/classes/fonts/DejaVuSans-Bold.ttf
RUN jar tf target/resumeforge-ai-1.0.0.jar | grep -q "BOOT-INF/classes/fonts/DejaVuSans.ttf" \
    || (echo "FATAL: DejaVuSans.ttf is missing from the JAR. Ensure src/main/resources/fonts/DejaVuSans.ttf exists and pom.xml <resources> includes **/*" && exit 1)

RUN jar tf target/resumeforge-ai-1.0.0.jar | grep -q "BOOT-INF/classes/fonts/DejaVuSans-Bold.ttf" \
    || (echo "FATAL: DejaVuSans-Bold.ttf is missing from the JAR. Ensure src/main/resources/fonts/DejaVuSans-Bold.ttf exists and pom.xml <resources> includes **/*" && exit 1)

# ── Runtime stage ─────────────────────────────────────────────────────────────
# FIX-7: Pin to eclipse-temurin:21-jre-jammy (Ubuntu 22.04 LTS).
# The untagged eclipse-temurin:21-jre resolves to whichever OS variant the
# maintainer last pushed -- this can change between deploys.  Alpine-based
# variants lack glibc which PDFBox native font subsystem requires.
# jammy = Ubuntu 22.04 LTS, has glibc, stable, deterministic.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /app/target/resumeforge-ai-1.0.0.jar app.jar

EXPOSE 8080

# ─────────────────────────────────────────────────────────────────────────────
# FIX-1 [CRITICAL]: -Djava.awt.headless=true
#   PDFBox 3.0.2 calls into AWT internally when loading fonts via
#   PDType0Font.load() and when calculating glyph widths in font.getWidth().
#   PDFBox's FontMapper enumerates system fonts using java.awt.GraphicsEnvironment.
#
#   In a headless Linux container (Render) with no X11 display server, AWT
#   initialisation throws java.awt.HeadlessException -- or hangs indefinitely
#   trying to connect to a display.
#
#   Without this flag: every PDF export call hits HeadlessException inside
#   PDType0Font.load() -> caught as Exception -> RuntimeException("Failed to
#   generate PDF") -> GlobalExceptionHandler returns HTTP 500.
#
# FIX-2 [CRITICAL]: -XX:+UseContainerSupport
#   Without this flag, the JVM reads the HOST machine total RAM to size the
#   heap, not the container cgroup limit.  Render's host machines have far more
#   RAM than the 512 MB allocated to your container.  When PDFBox buffers font
#   data and POI buffers DOCX XML during export, the container hits its cgroup
#   limit and is OOM-killed.  Render surfaces this as HTTP 502 or 500 with no
#   error in logs.
#
# FIX-3: -XX:MaxRAMPercentage=70.0
#   With UseContainerSupport, the JVM defaults to 25% of container RAM for
#   heap = 128 MB on 512 MB.  Spring Boot itself needs ~100-150 MB.  PDFBox
#   loads ~5 MB per font plus rendered pages into heap.  128 MB is not enough.
#   70% = 358 MB heap.  Remaining 154 MB covers OS + JVM thread stacks +
#   PDFBox/POI off-heap buffers.
#
# FIX-4: -Dfile.encoding=UTF-8
#   Locks the JVM platform encoding to UTF-8 on all eclipse-temurin variants.
#   ExportService.exportToTxt() already uses StandardCharsets.UTF_8 explicitly,
#   but Spring Boot logging and some JRE internals use the platform default.
# ─────────────────────────────────────────────────────────────────────────────
ENTRYPOINT ["java", \
  "-Djava.awt.headless=true", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=70.0", \
  "-Dfile.encoding=UTF-8", \
  "-jar", "app.jar"]