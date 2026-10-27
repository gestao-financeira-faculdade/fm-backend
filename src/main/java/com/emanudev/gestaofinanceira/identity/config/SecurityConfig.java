package com.emanudev.gestaofinanceira.identity.config;

import com.emanudev.gestaofinanceira.shared.config.ApplicationProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(ApplicationProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecretKey jwtSecretKey(ApplicationProperties applicationProperties) {
        return new SecretKeySpec(
                applicationProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("role");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            ApplicationProperties applicationProperties
    ) throws Exception {
        URI unauthorizedTypeUri = applicationProperties.buildErrorTypeUri("UNAUTHORIZED");
        URI forbiddenTypeUri = applicationProperties.buildErrorTypeUri("ACCESS_DENIED");

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // Authenticated identity endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()

                        // Audit logs: strictly ADMIN and AUDITOR (ANALISTA blocked with 403)
                        .requestMatchers("/api/v1/audit-logs", "/api/v1/audit-logs/**").hasAnyRole("ADMIN", "AUDITOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/entries/*/audit-history").hasAnyRole("ADMIN", "AUDITOR")

                        // Simulations: POST/mutations strictly ADMIN; GET allowed for all authenticated roles
                        .requestMatchers(HttpMethod.GET, "/api/v1/simulations", "/api/v1/simulations/**").hasAnyRole("ADMIN", "ANALISTA", "AUDITOR")
                        .requestMatchers("/api/v1/simulations", "/api/v1/simulations/**").hasRole("ADMIN")

                        // Reports & Dashboard: GET and POST /api/v1/reports/export allowed for ADMIN, ANALISTA, AUDITOR
                        .requestMatchers(HttpMethod.GET, "/api/v1/dashboard/**", "/api/v1/reports/**", "/api/v1/entries/export/**").hasAnyRole("ADMIN", "ANALISTA", "AUDITOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/reports/export", "/api/v1/reports/export/**").hasAnyRole("ADMIN", "ANALISTA", "AUDITOR")
                        .requestMatchers("/api/v1/dashboard/**", "/api/v1/reports/**").hasAnyRole("ADMIN", "ANALISTA", "AUDITOR")

                        // Logical deletion (Soft Delete) of financial entries: strictly ADMIN
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/entries", "/api/v1/entries/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/entries/*/cancel").hasRole("ADMIN")

                        // Financial mutations: allowed for ADMIN and ANALISTA (AUDITOR blocked with 403)
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/entries", "/api/v1/entries/**",
                                "/api/v1/accounts", "/api/v1/accounts/**",
                                "/api/v1/financial-accounts", "/api/v1/financial-accounts/**",
                                "/api/v1/categories", "/api/v1/categories/**",
                                "/api/v1/counterparties", "/api/v1/counterparties/**"
                        ).hasAnyRole("ADMIN", "ANALISTA")
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/entries", "/api/v1/entries/**",
                                "/api/v1/accounts", "/api/v1/accounts/**",
                                "/api/v1/financial-accounts", "/api/v1/financial-accounts/**",
                                "/api/v1/categories", "/api/v1/categories/**",
                                "/api/v1/counterparties", "/api/v1/counterparties/**"
                        ).hasAnyRole("ADMIN", "ANALISTA")
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/v1/entries", "/api/v1/entries/**",
                                "/api/v1/accounts", "/api/v1/accounts/**",
                                "/api/v1/financial-accounts", "/api/v1/financial-accounts/**",
                                "/api/v1/categories", "/api/v1/categories/**",
                                "/api/v1/counterparties", "/api/v1/counterparties/**"
                        ).hasAnyRole("ADMIN", "ANALISTA")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/accounts", "/api/v1/accounts/**",
                                "/api/v1/financial-accounts", "/api/v1/financial-accounts/**",
                                "/api/v1/categories", "/api/v1/categories/**",
                                "/api/v1/counterparties", "/api/v1/counterparties/**"
                        ).hasAnyRole("ADMIN", "ANALISTA")

                        // Read access to API resources for ADMIN, ANALISTA, AUDITOR
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole("ADMIN", "ANALISTA", "AUDITOR")

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((request, response, authException) -> writeProblemJson(
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                unauthorizedTypeUri,
                                "Unauthorized",
                                "Token de autenticação ausente, inválido ou expirado.",
                                "UNAUTHORIZED"
                        ))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeProblemJson(
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
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                unauthorizedTypeUri,
                                "Unauthorized",
                                "Autenticação obrigatória para acessar este recurso.",
                                "UNAUTHORIZED"
                        ))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeProblemJson(
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
            HttpServletResponse response,
            int status,
            URI typeUri,
            String title,
            String detail,
            String errorCode
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"type\":\"%s\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\",\"errorCode\":\"%s\",\"error_code\":\"%s\"}"
                        .formatted(typeUri, title, status, detail, errorCode, errorCode)
        );
    }
}
