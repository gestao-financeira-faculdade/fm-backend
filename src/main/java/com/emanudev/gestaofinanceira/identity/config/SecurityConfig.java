package com.emanudev.gestaofinanceira.identity.config;

import com.emanudev.gestaofinanceira.identity.adapter.out.security.ActiveUserJwtAuthenticationConverter;
import com.emanudev.gestaofinanceira.identity.application.port.out.AccessTokenBlocklistPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.UserRepositoryPort;
import com.emanudev.gestaofinanceira.shared.config.ApplicationProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.emanudev.gestaofinanceira.shared.adapter.in.web.CorrelationMdcFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.MDC;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(ApplicationProperties.class)
public class SecurityConfig {

    private static final String[] REFERENCE_DATA_ADMIN_ONLY = {
            "/v1/accounts", "/v1/accounts/**",
            "/v1/categories", "/v1/categories/**"
    };

    private static final String[] OPERATIONAL_RESOURCES = {
            "/v1/entries", "/v1/entries/**",
            "/v1/counterparties", "/v1/counterparties/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    static final String DEV_DEFAULT_JWT_SECRET = "default-32-bytes-secret-key-for-dev-and-test-env-2026";
    static final int MIN_JWT_SECRET_BYTES = 32;

    @Bean
    public SecretKey jwtSecretKey(ApplicationProperties applicationProperties, Environment environment) {
        byte[] secret = validatedJwtSecret(applicationProperties.getJwtSecret(), environment);
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    static byte[] validatedJwtSecret(String secret, Environment environment) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET must have at least " + MIN_JWT_SECRET_BYTES + " bytes for HS256.");
        }
        if (DEV_DEFAULT_JWT_SECRET.equals(secret) && !environment.matchesProfiles("dev", "test")) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET is not set. The built-in secret is only allowed with the dev or test profile.");
        }
        return bytes;
    }

    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            ApplicationProperties applicationProperties,
            AccessTokenBlocklistPort accessTokenBlocklist
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(applicationProperties.getJwtIssuer()),
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<String>(JwtClaimNames.JTI, Objects::nonNull),
                jwt -> jwt.getId() != null && accessTokenBlocklist.isBlocked(jwt.getId())
                        ? OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                OAuth2ErrorCodes.INVALID_TOKEN, "Token has been revoked", null))
                        : OAuth2TokenValidatorResult.success()
        ));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public ActiveUserJwtAuthenticationConverter jwtAuthenticationConverter(UserRepositoryPort userRepository) {
        return new ActiveUserJwtAuthenticationConverter(userRepository);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(ApplicationProperties applicationProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(applicationProperties.corsAllowedOriginList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Location", "ETag", "Content-Disposition", "Retry-After"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ActiveUserJwtAuthenticationConverter jwtAuthenticationConverter,
            ApplicationProperties applicationProperties
    ) throws Exception {
        URI unauthorizedTypeUri = applicationProperties.buildErrorTypeUri("UNAUTHORIZED");
        URI forbiddenTypeUri = applicationProperties.buildErrorTypeUri("ACCESS_DENIED");

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(HttpMethod.POST, "/v1/auth/login", "/v1/auth/refresh").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus").hasRole("ADMIN")
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // Authenticated identity endpoints
                        .requestMatchers(HttpMethod.POST, "/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/v1/auth/me").authenticated()

                        // Audit logs: strictly ADMIN and AUDITOR (ANALISTA blocked with 403)
                        .requestMatchers("/v1/audit-logs", "/v1/audit-logs/**").hasAnyRole("ADMIN", "AUDITOR")
                        .requestMatchers(HttpMethod.GET, "/v1/entries/*/audit-history").hasAnyRole("ADMIN", "AUDITOR")

                        // Simulations: POST/mutations strictly ADMIN; GET allowed for all authenticated roles
                        .requestMatchers(HttpMethod.GET, "/v1/simulations", "/v1/simulations/**").hasAnyRole("ADMIN", "ANALISTA", "AUDITOR")
                        .requestMatchers("/v1/simulations", "/v1/simulations/**").hasRole("ADMIN")

                        // Reports & Dashboard: read-only for ADMIN, ANALISTA, AUDITOR
                        .requestMatchers(HttpMethod.GET, "/v1/dashboard/**", "/v1/reports/**").hasAnyRole("ADMIN", "ANALISTA", "AUDITOR")
                        .requestMatchers("/v1/dashboard/**", "/v1/reports/**").hasAnyRole("ADMIN", "ANALISTA", "AUDITOR")

                        // Logical deletion (Soft Delete) of financial entries: strictly ADMIN
                        .requestMatchers(HttpMethod.DELETE, "/v1/entries", "/v1/entries/**").hasRole("ADMIN")

                        // Reference data (accounts and categories): strictly ADMIN
                        .requestMatchers(HttpMethod.POST, REFERENCE_DATA_ADMIN_ONLY).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, REFERENCE_DATA_ADMIN_ONLY).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, REFERENCE_DATA_ADMIN_ONLY).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, REFERENCE_DATA_ADMIN_ONLY).hasRole("ADMIN")

                        // Entries and counterparties: ADMIN and ANALISTA (AUDITOR blocked with 403)
                        .requestMatchers(HttpMethod.POST, OPERATIONAL_RESOURCES).hasAnyRole("ADMIN", "ANALISTA")
                        .requestMatchers(HttpMethod.PUT, OPERATIONAL_RESOURCES).hasAnyRole("ADMIN", "ANALISTA")
                        .requestMatchers(HttpMethod.PATCH, OPERATIONAL_RESOURCES).hasAnyRole("ADMIN", "ANALISTA")
                        .requestMatchers(HttpMethod.DELETE, "/v1/counterparties", "/v1/counterparties/**").hasAnyRole("ADMIN", "ANALISTA")

                        // Read access to API resources for ADMIN, ANALISTA, AUDITOR
                        .requestMatchers(HttpMethod.GET, "/v1/**").hasAnyRole("ADMIN", "ANALISTA", "AUDITOR")

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((request, response, authException) -> writeProblemJson(
                                request,
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                unauthorizedTypeUri,
                                "Unauthorized",
                                "Token de autenticação ausente, inválido ou expirado.",
                                "UNAUTHORIZED"
                        ))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeProblemJson(
                                request,
                                response,
                                HttpServletResponse.SC_FORBIDDEN,
                                forbiddenTypeUri,
                                "Forbidden",
                                "Acesso negado para o perfil do usuário autenticado.",
                                "ACCESS_DENIED"
                        ))
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> writeProblemJson(
                                request,
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                unauthorizedTypeUri,
                                "Unauthorized",
                                "Autenticação obrigatória para acessar este recurso.",
                                "UNAUTHORIZED"
                        ))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeProblemJson(
                                request,
                                response,
                                HttpServletResponse.SC_FORBIDDEN,
                                forbiddenTypeUri,
                                "Forbidden",
                                "Acesso negado para o perfil do usuário autenticado.",
                                "ACCESS_DENIED"
                        ))
                );

        return http.build();
    }

    private static void writeProblemJson(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            URI typeUri,
            String title,
            String detail,
            String errorCode
    ) throws IOException {
        if (status == HttpServletResponse.SC_UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"gestao-financeira\"");
        }
        String traceId = Optional.ofNullable(MDC.get("traceId"))
                .or(() -> Optional.ofNullable(MDC.get(CorrelationMdcFilter.MDC_CORRELATION_ID)))
                .orElse("N/A");
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                ("{\"type\":\"%s\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\",\"instance\":\"%s\","
                        + "\"errorCode\":\"%s\",\"error_code\":\"%s\",\"timestamp\":\"%s\",\"traceId\":\"%s\"}")
                        .formatted(typeUri, title, status, detail, jsonSafe(request.getRequestURI()), errorCode, errorCode,
                                Instant.now(), jsonSafe(traceId))
        );
    }

    private static String jsonSafe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
