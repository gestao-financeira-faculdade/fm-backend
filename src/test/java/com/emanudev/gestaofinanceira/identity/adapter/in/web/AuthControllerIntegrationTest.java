package com.emanudev.gestaofinanceira.identity.adapter.in.web;

import com.emanudev.gestaofinanceira.identity.adapter.out.security.JwtTokenProviderAdapter;
import com.emanudev.gestaofinanceira.identity.adapter.out.security.SecurityActorContextAdapter;
import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.UserRepositoryPort;
import com.emanudev.gestaofinanceira.identity.application.usecase.AuthenticateUserUseCase;
import com.emanudev.gestaofinanceira.identity.application.usecase.GetCurrentUserUseCase;
import com.emanudev.gestaofinanceira.identity.application.usecase.LogoutUseCase;
import com.emanudev.gestaofinanceira.identity.application.usecase.RefreshTokenUseCase;
import com.emanudev.gestaofinanceira.identity.config.SecurityConfig;
import com.emanudev.gestaofinanceira.identity.domain.model.RefreshToken;
import com.emanudev.gestaofinanceira.identity.domain.model.Role;
import com.emanudev.gestaofinanceira.identity.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = AuthControllerIntegrationTest.TestConfig.class)
class AuthControllerIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-01T12:00:00Z");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenProviderAdapter tokenProvider;

    @Autowired
    private InMemoryUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    private User adminUser;
    private User analistaUser;
    private User auditorUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        userRepository.clear();
        String encodedPass = passwordEncoder.encode("Admin@123");

        adminUser = new User(
                UUID.fromString("0195f000-0000-7000-8000-000000000001"),
                "Administrador Financeiro",
                "admin@gestaofinanceira.com",
                encodedPass,
                Role.ADMIN,
                true,
                FIXED_NOW,
                FIXED_NOW
        );
        analistaUser = new User(
                UUID.fromString("0195f000-0000-7000-8000-000000000002"),
                "Analista Financeiro",
                "analista@gestaofinanceira.com",
                encodedPass,
                Role.ANALISTA,
                true,
                FIXED_NOW,
                FIXED_NOW
        );
        auditorUser = new User(
                UUID.fromString("0195f000-0000-7000-8000-000000000003"),
                "Auditor de Compliance",
                "auditor@gestaofinanceira.com",
                encodedPass,
                Role.AUDITOR,
                true,
                FIXED_NOW,
                FIXED_NOW
        );

        userRepository.save(adminUser);
        userRepository.save(analistaUser);
        userRepository.save(auditorUser);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login deve retornar 200 com accessToken, refreshToken e dados do usuário")
    void shouldLoginAndReturnTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@gestaofinanceira.com","password":"Admin@123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("admin@gestaofinanceira.com"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login com senha incorreta deve retornar 401 ProblemDetail")
    void shouldReturn401OnInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@gestaofinanceira.com","password":"WrongPassword"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error_code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("GET /api/v1/users/me deve retornar 401 sem token e 200 com token JWT válido")
    void shouldProtectUsersMeAndReturnProfileWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        String token = tokenProvider.issueAccessToken(analistaUser).accessToken();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(analistaUser.id().toString()))
                .andExpect(jsonPath("$.email").value("analista@gestaofinanceira.com"))
                .andExpect(jsonPath("$.role").value("ANALISTA"));
    }

    @Test
    @DisplayName("Matriz RBAC: AUDITOR deve ter leitura permitida e ser bloqueado (403) em qualquer mutação financeira")
    void auditorShouldReadButBeForbiddenToMutateFinancialResources() throws Exception {
        String auditorToken = tokenProvider.issueAccessToken(auditorUser).accessToken();

        // Leitura permitida em lançamentos e auditoria
        mockMvc.perform(get("/api/v1/entries")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());

        // Exportação permitida
        mockMvc.perform(post("/api/v1/reports/export")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());

        // Mutações bloqueadas com 403 Forbidden
        mockMvc.perform(post("/api/v1/entries")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/entries/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/simulations")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Matriz RBAC: ANALISTA deve criar lançamentos, mas receber 403 em /api/v1/audit-logs, POST /api/v1/simulations e DELETE /api/v1/entries")
    void analistaShouldCreateEntriesButBeForbiddenOnAuditLogsSimulationsAndDelete() throws Exception {
        String analistaToken = tokenProvider.issueAccessToken(analistaUser).accessToken();

        // Operações permitidas ao ANALISTA
        mockMvc.perform(post("/api/v1/entries")
                        .header("Authorization", "Bearer " + analistaToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/simulations/latest")
                        .header("Authorization", "Bearer " + analistaToken))
                .andExpect(status().isOk());

        // Operações bloqueadas (403 Forbidden) para ANALISTA
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + analistaToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/simulations")
                        .header("Authorization", "Bearer " + analistaToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/entries/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + analistaToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Matriz RBAC: ADMIN deve possuir acesso completo a simulações, exclusão lógica e trilha de auditoria")
    void adminShouldHaveFullAccessAcrossAllEndpoints() throws Exception {
        String adminToken = tokenProvider.issueAccessToken(adminUser).accessToken();

        mockMvc.perform(post("/api/v1/entries")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/entries/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/simulations")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @RestController
    static class RbacProbeController {

        @GetMapping("/api/v1/entries")
        public Map<String, String> listEntries() {
            return Map.of("status", "ok");
        }

        @PostMapping("/api/v1/entries")
        public Map<String, String> createEntry() {
            return Map.of("status", "created");
        }

        @DeleteMapping("/api/v1/entries/{id}")
        public Map<String, String> deleteEntry(@PathVariable String id) {
            return Map.of("deleted", id);
        }

        @PostMapping("/api/v1/accounts")
        public Map<String, String> createAccount() {
            return Map.of("status", "account_created");
        }

        @GetMapping("/api/v1/audit-logs")
        public Map<String, String> listAuditLogs() {
            return Map.of("status", "audit_ok");
        }

        @PostMapping("/api/v1/simulations")
        public Map<String, String> runSimulation() {
            return Map.of("status", "simulated");
        }

        @GetMapping("/api/v1/simulations/latest")
        public Map<String, String> latestSimulation() {
            return Map.of("status", "latest_simulation");
        }

        @PostMapping("/api/v1/reports/export")
        public Map<String, String> exportReport() {
            return Map.of("status", "exported");
        }
    }

    static class InMemoryUserRepository implements UserRepositoryPort {
        private final Map<UUID, User> byId = new ConcurrentHashMap<>();
        private final Map<String, User> byEmail = new ConcurrentHashMap<>();

        void clear() {
            byId.clear();
            byEmail.clear();
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.ofNullable(byEmail.get(email.toLowerCase()));
        }

        @Override
        public Optional<User> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public User save(User user) {
            byId.put(user.id(), user);
            byEmail.put(user.email().toLowerCase(), user);
            return user;
        }
    }

    static class InMemoryRefreshTokenRepository implements RefreshTokenRepositoryPort {
        private final Map<String, RefreshToken> byHash = new ConcurrentHashMap<>();

        @Override
        public RefreshToken save(RefreshToken refreshToken) {
            byHash.put(refreshToken.tokenHash(), refreshToken);
            return refreshToken;
        }

        @Override
        public Optional<RefreshToken> findByTokenHash(String tokenHash) {
            return Optional.ofNullable(byHash.get(tokenHash));
        }

        @Override
        public void revokeAllByUserId(UUID userId, Instant revokedAt) {
            byHash.replaceAll((hash, token) -> token.userId().equals(userId) ? token.revoke(revokedAt) : token);
        }
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        InMemoryUserRepository userRepository() {
            return new InMemoryUserRepository();
        }

        @Bean
        InMemoryRefreshTokenRepository refreshTokenRepository() {
            return new InMemoryRefreshTokenRepository();
        }

        @Bean
        JwtTokenProviderAdapter jwtTokenProviderAdapter(JwtEncoder jwtEncoder, Clock clock) {
            return new JwtTokenProviderAdapter(jwtEncoder, clock, 900L);
        }

        @Bean
        SecurityActorContextAdapter securityActorContextAdapter() {
            return new SecurityActorContextAdapter();
        }

        @Bean
        AuthenticateUserUseCase authenticateUserUseCase(
                UserRepositoryPort userRepository,
                RefreshTokenRepositoryPort refreshTokenRepository,
                JwtTokenProviderAdapter tokenProvider,
                PasswordEncoder passwordEncoder,
                Clock clock
        ) {
            return new AuthenticateUserUseCase(userRepository, refreshTokenRepository, tokenProvider, passwordEncoder, clock);
        }

        @Bean
        RefreshTokenUseCase refreshTokenUseCase(
                RefreshTokenRepositoryPort refreshTokenRepository,
                UserRepositoryPort userRepository,
                JwtTokenProviderAdapter tokenProvider,
                Clock clock
        ) {
            return new RefreshTokenUseCase(refreshTokenRepository, userRepository, tokenProvider, clock);
        }

        @Bean
        LogoutUseCase logoutUseCase(
                RefreshTokenRepositoryPort refreshTokenRepository,
                JwtTokenProviderAdapter tokenProvider,
                Clock clock
        ) {
            return new LogoutUseCase(refreshTokenRepository, tokenProvider, clock);
        }

        @Bean
        GetCurrentUserUseCase getCurrentUserUseCase(
                UserRepositoryPort userRepository,
                SecurityActorContextAdapter actorContextAdapter
        ) {
            return new GetCurrentUserUseCase(userRepository, actorContextAdapter);
        }

        @Bean
        AuthController authController(
                AuthenticateUserUseCase authenticateUserUseCase,
                RefreshTokenUseCase refreshTokenUseCase,
                LogoutUseCase logoutUseCase,
                GetCurrentUserUseCase getCurrentUserUseCase,
                SecurityActorContextAdapter actorContextAdapter
        ) {
            return new AuthController(
                    authenticateUserUseCase,
                    refreshTokenUseCase,
                    logoutUseCase,
                    getCurrentUserUseCase,
                    actorContextAdapter
            );
        }

        @Bean
        RbacProbeController rbacProbeController() {
            return new RbacProbeController();
        }
    }
}
