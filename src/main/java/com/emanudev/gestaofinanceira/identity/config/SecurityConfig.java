package com.emanudev.gestaofinanceira.identity.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String DEFAULT_JWT_SECRET =
            "default-32-bytes-secret-key-for-dev-and-test-env-2026";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecretKey jwtSecretKey(
            @Value("${app.security.jwt.secret:" + DEFAULT_JWT_SECRET + "}") String secret
    ) {
        String effectiveSecret = (secret == null || secret.length() < 32) ? DEFAULT_JWT_SECRET : secret;
        return new SecretKeySpec(effectiveSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
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
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            String role = jwt.getClaimAsString("role");
            if (role != null && !role.isBlank()) {
                String normalized = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                authorities.add(new SimpleGrantedAuthority(normalized));
            }
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                for (String item : roles) {
                    if (item != null && !item.isBlank()) {
                        String normalized = item.startsWith("ROLE_") ? item : "ROLE_" + item;
                        authorities.add(new SimpleGrantedAuthority(normalized));
                    }
                }
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
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

                        // Simulations: POST/mutations strictly ADMIN (ANALISTA and AUDITOR blocked with 403); GET allowed for all authenticated roles
                        .requestMatchers(HttpMethod.GET, "/api/v1/simulations", "/api/v1/simulations/**").hasAnyRole("ADMIN", "ANALISTA", "AUDITOR")
                        .requestMatchers("/api/v1/simulations", "/api/v1/simulations/**").hasRole("ADMIN")

                        // Reports & Dashboard: GET and POST /api/v1/reports/export allowed for ADMIN, ANALISTA, AUDITOR
                        .requestMatchers(HttpMethod.GET, "/api/v1/dashboard/**", "/api/v1/reports/**", "/api/v1/entries/export/**").hasAnyRole("ADMIN", "ANALISTA", "AUDITOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/reports/export", "/api/v1/reports/export/**").hasAnyRole("ADMIN", "ANALISTA", "AUDITOR")
                        .requestMatchers("/api/v1/dashboard/**", "/api/v1/reports/**").hasAnyRole("ADMIN", "ANALISTA", "AUDITOR")

                        // Logical deletion (Soft Delete) of financial entries: strictly ADMIN (ANALISTA and AUDITOR blocked with 403)
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

                        // Any other endpoint requires ADMIN by default if mutating, or authentication
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write("""
                                    {"type":"https://gestaofinanceira.com/errors/unauthorized","title":"Unauthorized","status":401,"detail":"Token de autenticação ausente, inválido ou expirado.","error_code":"UNAUTHORIZED"}""");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write("""
                                    {"type":"https://gestaofinanceira.com/errors/forbidden","title":"Forbidden","status":403,"detail":"Acesso negado para o perfil do usuário autenticado.","error_code":"ACCESS_DENIED"}""");
                        })
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write("""
                                    {"type":"https://gestaofinanceira.com/errors/unauthorized","title":"Unauthorized","status":401,"detail":"Autenticação obrigatória para acessar este recurso.","error_code":"UNAUTHORIZED"}""");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write("""
                                    {"type":"https://gestaofinanceira.com/errors/forbidden","title":"Forbidden","status":403,"detail":"Acesso negado para o perfil do usuário autenticado.","error_code":"ACCESS_DENIED"}""");
                        })
                );

        return http.build();
    }
}
