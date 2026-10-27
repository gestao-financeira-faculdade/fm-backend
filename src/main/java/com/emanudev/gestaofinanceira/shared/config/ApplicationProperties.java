package com.emanudev.gestaofinanceira.shared.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApplicationProperties {

    public static final String DEFAULT_ERROR_BASE_URI = "http://localhost:8080/errors";
    public static final String DEFAULT_JWT_ISSUER = "gestao-financeira";
    public static final String DEFAULT_JWT_SECRET = "default-32-bytes-secret-key-for-dev-and-test-env-2026";
    public static final long DEFAULT_JWT_ACCESS_TOKEN_TTL_SECONDS = 900L;
    public static final long DEFAULT_JWT_REFRESH_TOKEN_TTL_DAYS = 7L;
    public static final String DEFAULT_S3_ENDPOINT = "http://localhost:4566";
    public static final String DEFAULT_S3_REGION = "us-east-1";
    public static final String DEFAULT_S3_BUCKET = "fiscal-attachments";
    public static final String DEFAULT_S3_ACCESS_KEY = "test";
    public static final String DEFAULT_S3_SECRET_KEY = "test";
    public static final boolean DEFAULT_S3_PATH_STYLE_ACCESS = true;
    public static final boolean DEFAULT_S3_AUTO_CREATE_BUCKET = true;
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
    private final String s3Endpoint;
    private final String s3Region;
    private final String s3Bucket;
    private final String s3AccessKey;
    private final String s3SecretKey;
    private final boolean s3PathStyleAccessEnabled;
    private final boolean s3AutoCreateBucket;
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
                DEFAULT_S3_ENDPOINT,
                DEFAULT_S3_REGION,
                DEFAULT_S3_BUCKET,
                DEFAULT_S3_ACCESS_KEY,
                DEFAULT_S3_SECRET_KEY,
                DEFAULT_S3_PATH_STYLE_ACCESS,
                DEFAULT_S3_AUTO_CREATE_BUCKET,
                DEFAULT_SIMULATION_GRPC_HOST,
                DEFAULT_SIMULATION_GRPC_PORT,
                DEFAULT_SIMULATION_GRPC_ENABLED,
                DEFAULT_SIMULATION_GRPC_FALLBACK_ENABLED,
                DEFAULT_SIMULATION_GRPC_DEADLINE_MS,
                DEFAULT_SIMULATION_MAX_CONCURRENT
        );
    }

    @Autowired
    public ApplicationProperties(
            @Value("${app.api.error-base-uri:" + DEFAULT_ERROR_BASE_URI + "}") String errorBaseUri,
            @Value("${app.security.jwt.issuer:" + DEFAULT_JWT_ISSUER + "}") String jwtIssuer,
            @Value("${app.security.jwt.secret:" + DEFAULT_JWT_SECRET + "}") String jwtSecret,
            @Value("${app.security.jwt.access-token-ttl-seconds:" + DEFAULT_JWT_ACCESS_TOKEN_TTL_SECONDS + "}") long jwtAccessTokenTtlSeconds,
            @Value("${app.security.jwt.refresh-token-ttl-days:" + DEFAULT_JWT_REFRESH_TOKEN_TTL_DAYS + "}") long jwtRefreshTokenTtlDays,
            @Value("${app.storage.s3.endpoint:" + DEFAULT_S3_ENDPOINT + "}") String s3Endpoint,
            @Value("${app.storage.s3.region:" + DEFAULT_S3_REGION + "}") String s3Region,
            @Value("${app.storage.s3.bucket:" + DEFAULT_S3_BUCKET + "}") String s3Bucket,
            @Value("${app.storage.s3.access-key:" + DEFAULT_S3_ACCESS_KEY + "}") String s3AccessKey,
            @Value("${app.storage.s3.secret-key:" + DEFAULT_S3_SECRET_KEY + "}") String s3SecretKey,
            @Value("${app.storage.s3.path-style-access-enabled:" + DEFAULT_S3_PATH_STYLE_ACCESS + "}") boolean s3PathStyleAccessEnabled,
            @Value("${app.storage.s3.auto-create-bucket:" + DEFAULT_S3_AUTO_CREATE_BUCKET + "}") boolean s3AutoCreateBucket,
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
        this.s3Endpoint = (s3Endpoint == null || s3Endpoint.isBlank()) ? DEFAULT_S3_ENDPOINT : s3Endpoint.trim();
        this.s3Region = (s3Region == null || s3Region.isBlank()) ? DEFAULT_S3_REGION : s3Region.trim();
        this.s3Bucket = (s3Bucket == null || s3Bucket.isBlank()) ? DEFAULT_S3_BUCKET : s3Bucket.trim();
        this.s3AccessKey = (s3AccessKey == null || s3AccessKey.isBlank()) ? DEFAULT_S3_ACCESS_KEY : s3AccessKey.trim();
        this.s3SecretKey = (s3SecretKey == null || s3SecretKey.isBlank()) ? DEFAULT_S3_SECRET_KEY : s3SecretKey.trim();
        this.s3PathStyleAccessEnabled = s3PathStyleAccessEnabled;
        this.s3AutoCreateBucket = s3AutoCreateBucket;
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

    public String getS3Endpoint() {
        return s3Endpoint;
    }

    public String getS3Region() {
        return s3Region;
    }

    public String getS3Bucket() {
        return s3Bucket;
    }

    public String getS3AccessKey() {
        return s3AccessKey;
    }

    public String getS3SecretKey() {
        return s3SecretKey;
    }

    public boolean isS3PathStyleAccessEnabled() {
        return s3PathStyleAccessEnabled;
    }

    public boolean isS3AutoCreateBucket() {
        return s3AutoCreateBucket;
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
