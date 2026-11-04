package com.emanudev.gestaofinanceira.shared.config;

import java.net.URI;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class ApplicationProperties {

    @Value("${app.api.error-base-uri}")
    private String errorBaseUri;

    @Value("${app.security.jwt.issuer}")
    private String jwtIssuer;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.jwt.access-token-ttl-seconds}")
    private long jwtAccessTokenTtlSeconds;

    @Value("${app.security.jwt.refresh-token-ttl-days}")
    private long jwtRefreshTokenTtlDays;

    @Value("${app.storage.s3.endpoint}")
    private String s3Endpoint;

    @Value("${app.storage.s3.region}")
    private String s3Region;

    @Value("${app.storage.s3.bucket}")
    private String s3Bucket;

    @Value("${app.storage.s3.access-key}")
    private String s3AccessKey;

    @Value("${app.storage.s3.secret-key}")
    private String s3SecretKey;

    @Value("${app.storage.s3.path-style-access-enabled}")
    private boolean s3PathStyleAccessEnabled;

    @Value("${app.storage.s3.auto-create-bucket}")
    private boolean s3AutoCreateBucket;

    @Value("${app.simulation.grpc.host}")
    private String simulationGrpcHost;

    @Value("${app.simulation.grpc.port}")
    private int simulationGrpcPort;

    @Value("${app.simulation.grpc.enabled}")
    private boolean simulationGrpcEnabled;

    @Value("${app.simulation.grpc.fallback-enabled}")
    private boolean simulationGrpcFallbackEnabled;

    @Value("${app.simulation.grpc.deadline-ms}")
    private long simulationGrpcDeadlineMs;

    @Value("${app.simulation.max-concurrent}")
    private int simulationMaxConcurrent;

    public URI buildErrorTypeUri(String errorCode) {
        String base = errorBaseUri == null
                ? ""
                : (errorBaseUri.endsWith("/") ? errorBaseUri.substring(0, errorBaseUri.length() - 1) : errorBaseUri);
        String slug = (errorCode == null || errorCode.isBlank())
                ? "unexpected-error"
                : errorCode.trim().toLowerCase().replace('_', '-');
        return URI.create(base + "/" + slug);
    }
}
