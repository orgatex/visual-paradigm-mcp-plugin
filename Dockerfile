# Multi-stage build for Visual Paradigm MCP Plugin (proxy mode)
FROM maven:3.9-eclipse-temurin-11 AS builder

WORKDIR /build

# Copy pom.xml and stub generator first for dependency caching
COPY pom.xml docker/generate-stubs.sh ./

# Generate VP API stub JAR for compilation
RUN chmod +x generate-stubs.sh && ./generate-stubs.sh stub-libs

# Copy source code
COPY src ./src

# Build the plugin with assembly
RUN mvn clean package -DskipTests \
    -Dspotbugs.skip=true -Dpmd.skip=true -Dcheckstyle.skip=true \
    -Dfmt.skip=true \
    -Dvp.lib.dir=/build/stub-libs

# Runtime stage
FROM eclipse-temurin:11-jre

WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Copy the assembly output (main jar + dependencies)
COPY --from=builder /build/target/visual-paradigm-mcp-plugin/ /app/

# Copy VP API stubs
COPY --from=builder /build/stub-libs/openapi.jar /app/lib/openapi.jar

# Expose MCP server port
EXPOSE 2026

# VP API URL (default: host machine's VP instance)
ENV VP_API_URL=http://host.docker.internal:2026
ENV MCP_PORT=2026

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
    CMD curl -fs -X POST -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","id":1,"method":"ping"}' http://localhost:2026/mcp || exit 1

# Run the proxy MCP server
ENTRYPOINT ["java", "-cp", "/app/lib/*:/app/visual-paradigm-mcp-plugin.jar", "com.brunnen.vp.mcp.StandaloneServer"]
