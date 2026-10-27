package com.emanudev.gestaofinanceira.shared.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApplicationProperties {

    public static final String DEFAULT_ERROR_BASE_URI = "http://localhost:8080/errors";
    public static final String DEFAULT_JWT_ISSUER = "gestao-financeira";
    public static final String DEFAULT_JWT_SECRET = "default-32-bytes-secret-key-for-dev-and-test-env-2026";
    public static final long DEFAULT_JWT_ACCESS_TOKEN_TTL_SECONDS = 900L;
    public static final long DEFAULT_JWT_REFRESH_TOKEN_TTL_DAYS = 7L;
    public static final String DEFAULT_ATTACHMENTS_DIR = "./target/attachments-storage";
    public static final String DEFAULT_SIMULATION_GRPC_HOST = "localhost";
    public static final int DEFAULT_SIMULATION_GRPC_PORT = 50051;
    public static final boolean DEFAULT_SIMULATION_GRPC_ENABLED = false;
    public static final boolean DEFAULT_SIMULATION_GRPC_FALLBACK_ENABLED = true;
    public static final long DEFAULT_SIMULATION_GRPC_DEADLINE_MS = 5000L;
    public static final int DEFAULT_SIMULATION_MAX_CONCURRENT = 4;

    private final String errorBaseUri;
    private final String jwtIssuer;
    private final String jwtSecret;
    private final long jwtAccessTokenTtlSeconds;
    private final long jwtRefreshTokenTtlDays;
    private final String attachmentsDir;
    private final String simulationGrpcHost;
    private final int simulationGrpcPort;
    private final boolean simulationGrpcEnabled;
    private final boolean simulationGrpcFallbackEnabled;
    private final long simulationGrpcDeadlineMs;
    private final int simulationMaxConcurrent;

    public ApplicationProperties() {
        this(
                DEFAULT_ERROR_BASE_URI,
                DEFAULT_JWT_ISSUER,
                DEFAULT_JWT_SECRET,
                DEFAULT_JWT_ACCESS_TOKEN_TTL_SECONDS,
                DEFAULT_JWT_REFRESH_TOKEN_TTL_DAYS,
                DEFAULT_ATTACHMENTS_DIR,
                DEFAULT_SIMULATION_GRPC_HOST,
                DEFAULT_SIMULATION_GRPC_PORT,
                DEFAULT_SIMULATION_GRPC_ENABLED,
                DEFAULT_SIMULATION_GRPC_FALLBACK_ENABLED,
                DEFAULT_SIMULATION_GRPC_DEADLINE_MS,
                DEFAULT_SIMULATION_MAX_CONCURRENT
        );
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ApplicationProperties(
            @Value("${app.api.error-base-uri:" + DEFAULT_ERROR_BASE_URI + "}") String errorBaseUri,
            @Value("${app.security.jwt.issuer:" + DEFAULT_JWT_ISSUER + "}") String jwtIssuer,
            @Value("${app.security.jwt.secret:" + DEFAULT_JWT_SECRET + "}") String jwtSecret,
            @Value("${app.security.jwt.access-token-ttl-seconds:" + DEFAULT_JWT_ACCESS_TOKEN_TTL_SECONDS + "}") long jwtAccessTokenTtlSeconds,
            @Value("${app.security.jwt.refresh-token-ttl-days:" + DEFAULT_JWT_REFRESH_TOKEN_TTL_DAYS + "}") long jwtRefreshTokenTtlDays,
            @Value("${app.storage.attachments-dir:" + DEFAULT_ATTACHMENTS_DIR + "}") String attachmentsDir,
            @Value("${app.simulation.grpc.host:" + DEFAULT_SIMULATION_GRPC_HOST + "}") String simulationGrpcHost,
            @Value("${app.simulation.grpc.port:" + DEFAULT_SIMULATION_GRPC_PORT + "}") int simulationGrpcPort,
            @Value("${app.simulation.grpc.enabled:" + DEFAULT_SIMULATION_GRPC_ENABLED + "}") boolean simulationGrpcEnabled,
            @Value("${app.simulation.grpc.fallback-enabled:" + DEFAULT_SIMULATION_GRPC_FALLBACK_ENABLED + "}") boolean simulationGrpcFallbackEnabled,
            @Value("${app.simulation.grpc.deadline-ms:" + DEFAULT_SIMULATION_GRPC_DEADLINE_MS + "}") long simulationGrpcDeadlineMs,
            @Value("${app.simulation.max-concurrent:" + DEFAULT_SIMULATION_MAX_CONCURRENT + "}") int simulationMaxConcurrent
    ) {
        this.errorBaseUri = normalizeBaseUri(errorBaseUri);
        this.jwtIssuer = (jwtIssuer == null || jwtIssuer.isBlank()) ? DEFAULT_JWT_ISSUER : jwtIssuer.trim();
        this.jwtSecret = (jwtSecret == null || jwtSecret.length() < 32) ? DEFAULT_JWT_SECRET : jwtSecret;
        this.jwtAccessTokenTtlSeconds = jwtAccessTokenTtlSeconds > 0 ? jwtAccessTokenTtlSeconds : DEFAULT_JWT_ACCESS_TOKEN_TTL_SECONDS;
        this.jwtRefreshTokenTtlDays = jwtRefreshTokenTtlDays > 0 ? jwtRefreshTokenTtlDays : DEFAULT_JWT_REFRESH_TOKEN_TTL_DAYS;
        this.attachmentsDir = (attachmentsDir == null || attachmentsDir.isBlank()) ? DEFAULT_ATTACHMENTS_DIR : attachmentsDir.trim();
        this.simulationGrpcHost = (simulationGrpcHost == null || simulationGrpcHost.isBlank()) ? DEFAULT_SIMULATION_GRPC_HOST : simulationGrpcHost.trim();
        this.simulationGrpcPort = simulationGrpcPort > 0 ? simulationGrpcPort : DEFAULT_SIMULATION_GRPC_PORT;
        this.simulationGrpcEnabled = simulationGrpcEnabled;
        this.simulationGrpcFallbackEnabled = simulationGrpcFallbackEnabled;
        this.simulationGrpcDeadlineMs = simulationGrpcDeadlineMs > 0 ? simulationGrpcDeadlineMs : DEFAULT_SIMULATION_GRPC_DEADLINE_MS;
        this.simulationMaxConcurrent = simulationMaxConcurrent > 0 ? simulationMaxConcurrent : DEFAULT_SIMULATION_MAX_CONCURRENT;
    }

    public String getErrorBaseUri() {
        return errorBaseUri;
    }

    public URI buildErrorTypeUri(String errorCode) {
        String slug = (errorCode == null || errorCode.isBlank())
                ? "unexpected-error"
                : errorCode.trim().toLowerCase().replace('_', '-');
        return URI.create(errorBaseUri + "/" + slug);
    }

    public String getJwtIssuer() {
        return jwtIssuer;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public long getJwtAccessTokenTtlSeconds() {
        return jwtAccessTokenTtlSeconds;
    }

    public long getJwtRefreshTokenTtlDays() {
        return jwtRefreshTokenTtlDays;
    }

    public String getAttachmentsDir() {
        return attachmentsDir;
    }

    public String getSimulationGrpcHost() {
        return simulationGrpcHost;
    }

    public int getSimulationGrpcPort() {
        return simulationGrpcPort;
    }

    public boolean isSimulationGrpcEnabled() {
        return simulationGrpcEnabled;
    }

    public boolean isSimulationGrpcFallbackEnabled() {
        return simulationGrpcFallbackEnabled;
    }

    public long getSimulationGrpcDeadlineMs() {
        return simulationGrpcDeadlineMs;
    }

    public int getSimulationMaxConcurrent() {
        return simulationMaxConcurrent;
    }

    private static String normalizeBaseUri(String rawUri) {
        if (rawUri == null || rawUri.isBlank()) {
            return DEFAULT_ERROR_BASE_URI;
        }
        String trimmed = rawUri.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
