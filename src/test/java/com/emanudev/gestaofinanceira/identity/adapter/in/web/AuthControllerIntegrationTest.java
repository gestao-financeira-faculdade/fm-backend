package com.emanudev.gestaofinanceira.identity.adapter.in.web;

import com.emanudev.gestaofinanceira.identity.adapter.out.security.JwtTokenProviderAdapter;
import com.emanudev.gestaofinanceira.identity.adapter.out.security.SecurityActorContextAdapter;
import com.emanudev.gestaofinanceira.identity.application.port.out.AccessTokenBlocklistPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.LoginAttemptPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort;
import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort.ConsumeStatus;
import com.emanudev.gestaofinanceira.identity.application.port.out.RefreshTokenRepositoryPort.Consumption;
import com.emanudev.gestaofinanceira.identity.application.port.out.UserRepositoryPort;
import com.emanudev.gestaofinanceira.identity.application.usecase.AuthenticateUserUseCase;
import com.emanudev.gestaofinanceira.identity.application.usecase.GetCurrentUserUseCase;
import com.emanudev.gestaofinanceira.identity.application.usecase.LogoutUseCase;
import com.emanudev.gestaofinanceira.identity.application.usecase.RefreshTokenUseCase;
import com.emanudev.gestaofinanceira.identity.config.SecurityConfig;
import com.emanudev.gestaofinanceira.shared.adapter.in.web.GlobalExceptionHandler;
import com.emanudev.gestaofinanceira.shared.config.ApplicationProperties;
import com.emanudev.gestaofinanceira.identity.domain.model.RefreshToken;
import com.emanudev.gestaofinanceira.identity.domain.model.Role;
import com.emanudev.gestaofinanceira.identity.domain.model.User;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ActiveProfiles("test")
@ContextConfiguration(
        classes = AuthControllerIntegrationTest.TestConfig.class,
        initializers = ConfigDataApplicationContextInitializer.class
)
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

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private InMemoryLoginAttempts loginAttempts;

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
        loginAttempts.clear();
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
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@gestaofinanceira.com","password":"Admin@123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("admin@gestaofinanceira.com"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login com senha incorreta deve retornar 401 ProblemDetail")
    void shouldReturn401OnInvalidCredentials() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@gestaofinanceira.com","password":"WrongPassword"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error_code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me deve retornar 401 sem token e 200 com token JWT válido")
    void shouldProtectUsersMeAndReturnProfileWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        String token = tokenProvider.issueAccessToken(analistaUser).accessToken();

        mockMvc.perform(get("/v1/auth/me")
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
        mockMvc.perform(get("/v1/entries")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/audit-logs")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());

        // Exportação permitida
        mockMvc.perform(post("/v1/reports/export")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());

        // Mutações bloqueadas com 403 Forbidden
        mockMvc.perform(post("/v1/entries")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/v1/entries/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/v1/simulations")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Matriz RBAC: ANALISTA deve criar lançamentos, mas receber 403 em /api/v1/audit-logs, POST /api/v1/simulations e DELETE /api/v1/entries")
    void analistaShouldCreateEntriesButBeForbiddenOnAuditLogsSimulationsAndDelete() throws Exception {
        String analistaToken = tokenProvider.issueAccessToken(analistaUser).accessToken();

        // Operações permitidas ao ANALISTA
        mockMvc.perform(post("/v1/entries")
                        .header("Authorization", "Bearer " + analistaToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/simulations/latest")
                        .header("Authorization", "Bearer " + analistaToken))
                .andExpect(status().isOk());

        // Operações bloqueadas (403 Forbidden) para ANALISTA
        mockMvc.perform(get("/v1/audit-logs")
                        .header("Authorization", "Bearer " + analistaToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/v1/simulations")
                        .header("Authorization", "Bearer " + analistaToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/v1/entries/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + analistaToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Matriz RBAC: ADMIN deve possuir acesso completo a simulações, exclusão lógica e trilha de auditoria")
    void adminShouldHaveFullAccessAcrossAllEndpoints() throws Exception {
        String adminToken = tokenProvider.issueAccessToken(adminUser).accessToken();

        mockMvc.perform(post("/v1/entries")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/v1/entries/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/simulations")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DEF-04: contas e categorias só aceitam mutação de ADMIN; contrapartes seguem com ADMIN e ANALISTA")
    void referenceDataMutationsShouldBeAdminOnly() throws Exception {
        String analistaToken = tokenProvider.issueAccessToken(analistaUser).accessToken();
        String adminToken = tokenProvider.issueAccessToken(adminUser).accessToken();

        for (String path : new String[]{"/v1/accounts", "/v1/financial-accounts", "/v1/categories"}) {
            mockMvc.perform(post(path).header("Authorization", "Bearer " + analistaToken))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(path).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/v1/counterparties").header("Authorization", "Bearer " + analistaToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DEF-05/26: token sem exp, sem jti ou com emissor errado é rejeitado com 401")
    void shouldRejectTokensWithoutExpirationOrWithWrongIssuer() throws Exception {
        Instant now = Instant.now();
        String semExp = sign(baseClaims(adminUser, now).build());
        String semJti = sign(JwtClaimsSet.builder()
                .issuer("gestao-financeira").subject(adminUser.id().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(600)).build());
        String emissorErrado = sign(baseClaims(adminUser, now).issuer("outro-emissor").expiresAt(now.plusSeconds(600)).build());
        String valido = sign(baseClaims(adminUser, now).expiresAt(now.plusSeconds(600)).build());

        for (String token : new String[]{semExp, semJti, emissorErrado}) {
            mockMvc.perform(get("/v1/entries").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/v1/entries").header("Authorization", "Bearer " + valido))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DEF-06/25: token de usuário inexistente ou desativado é rejeitado, e o papel vem do cadastro, não do token")
    void shouldAuthorizeAgainstCurrentUserRecord() throws Exception {
        Instant now = Instant.now();
        String fantasma = sign(baseClaims(adminUser, now)
                .subject(UUID.randomUUID().toString()).expiresAt(now.plusSeconds(600)).build());
        mockMvc.perform(get("/v1/entries").header("Authorization", "Bearer " + fantasma))
                .andExpect(status().isUnauthorized());

        String papelForjado = sign(baseClaims(analistaUser, now).claim("role", "ADMIN").expiresAt(now.plusSeconds(600)).build());
        mockMvc.perform(delete("/v1/entries/" + UUID.randomUUID()).header("Authorization", "Bearer " + papelForjado))
                .andExpect(status().isForbidden());

        String auditorToken = tokenProvider.issueAccessToken(auditorUser).accessToken();
        userRepository.save(auditorUser.deactivate(now));
        mockMvc.perform(get("/v1/entries").header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DEF-25: logout invalida o access token na hora e revoga só a sessão atual")
    void logoutShouldRevokeCurrentAccessTokenAndOnlyThisSession() throws Exception {
        Map<String, String> sessaoA = login("admin@gestaofinanceira.com");
        Map<String, String> sessaoB = login("admin@gestaofinanceira.com");

        mockMvc.perform(post("/v1/auth/logout")
                        .header("Authorization", "Bearer " + sessaoA.get("accessToken"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + sessaoA.get("accessToken")))
                .andExpect(status().isUnauthorized());
        refresh(sessaoA.get("refreshToken")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/auth/me").header("Authorization", "Bearer " + sessaoB.get("accessToken")))
                .andExpect(status().isOk());
        refresh(sessaoB.get("refreshToken")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("DEF-25: logout com refresh token de outro usuário não derruba a sessão dele")
    void logoutShouldNotRevokeRefreshTokenOfAnotherUser() throws Exception {
        Map<String, String> analista = login("analista@gestaofinanceira.com");
        Map<String, String> auditor = login("auditor@gestaofinanceira.com");

        mockMvc.perform(post("/v1/auth/logout")
                        .header("Authorization", "Bearer " + analista.get("accessToken"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + auditor.get("refreshToken") + "\"}"))
                .andExpect(status().isNoContent());

        refresh(auditor.get("refreshToken")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("DEF-25: reutilizar um refresh já rotacionado revoga a sessão inteira")
    void reusingRotatedRefreshTokenShouldRevokeSession() throws Exception {
        Map<String, String> sessao = login("admin@gestaofinanceira.com");
        String primeiro = sessao.get("refreshToken");

        String rotacionado = JsonPath.read(refresh(primeiro).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.refreshToken");

        refresh(primeiro).andExpect(status().isUnauthorized());
        refresh(rotacionado).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DEF-26: após 5 falhas o login fica bloqueado com 429 e Retry-After, mesmo com a senha certa")
    void shouldLockLoginAfterRepeatedFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"admin@gestaofinanceira.com\",\"password\":\"Errada@" + i + "\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@gestaofinanceira.com\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.error_code").value("TOO_MANY_LOGIN_ATTEMPTS"));
    }

    @Test
    @DisplayName("DEF-27: preflight CORS da origem configurada é aceito e o de outra origem é recusado")
    void shouldAnswerCorsPreflightForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/v1/entries")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));

        mockMvc.perform(options("/v1/entries")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DEF-44: /actuator/prometheus exige autenticação de ADMIN")
    void prometheusShouldRequireAdmin() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer " + tokenProvider.issueAccessToken(analistaUser).accessToken()))
                .andExpect(status().isForbidden());
    }

    private Map<String, String> login(String email) throws Exception {
        String body = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Map.of(
                "accessToken", JsonPath.read(body, "$.accessToken"),
                "refreshToken", JsonPath.read(body, "$.refreshToken")
        );
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private static JwtClaimsSet.Builder baseClaims(User user, Instant now) {
        return JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer("gestao-financeira")
                .subject(user.id().toString())
                .issuedAt(now)
                .claim("role", user.role().name());
    }

    private String sign(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @RestController
    static class RbacProbeController {

        @PostMapping({"/v1/financial-accounts", "/v1/categories", "/v1/counterparties"})
        public Map<String, String> createReferenceData() {
            return Map.of("status", "created");
        }

        @GetMapping("/v1/entries")
        public Map<String, String> listEntries() {
            return Map.of("status", "ok");
        }

        @PostMapping("/v1/entries")
        public Map<String, String> createEntry() {
            return Map.of("status", "created");
        }

        @DeleteMapping("/v1/entries/{id}")
        public Map<String, String> deleteEntry(@PathVariable String id) {
            return Map.of("deleted", id);
        }

        @PostMapping("/v1/accounts")
        public Map<String, String> createAccount() {
            return Map.of("status", "account_created");
        }

        @GetMapping("/v1/audit-logs")
        public Map<String, String> listAuditLogs() {
            return Map.of("status", "audit_ok");
        }

        @PostMapping("/v1/simulations")
        public Map<String, String> runSimulation() {
            return Map.of("status", "simulated");
        }

        @GetMapping("/v1/simulations/latest")
        public Map<String, String> latestSimulation() {
            return Map.of("status", "latest_simulation");
        }

        @PostMapping("/v1/reports/export")
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
        public synchronized Consumption consume(String tokenHash, Instant now) {
            RefreshToken token = byHash.get(tokenHash);
            if (token == null) {
                return Consumption.notFound();
            }
            if (token.isRevoked()) {
                return new Consumption(ConsumeStatus.ALREADY_REVOKED, token);
            }
            RefreshToken consumed = token.revoke(now);
            byHash.put(tokenHash, consumed);
            return new Consumption(ConsumeStatus.CONSUMED, consumed);
        }

        @Override
        public void revokeFamily(UUID familyId, Instant revokedAt) {
            byHash.replaceAll((hash, token) -> token.familyId().equals(familyId) ? token.revoke(revokedAt) : token);
        }

        @Override
        public void revokeAllByUserId(UUID userId, Instant revokedAt) {
            byHash.replaceAll((hash, token) -> token.userId().equals(userId) ? token.revoke(revokedAt) : token);
        }
    }

    static class InMemoryAccessTokenBlocklist implements AccessTokenBlocklistPort {
        private final Set<String> blocked = ConcurrentHashMap.newKeySet();

        @Override
        public void block(String tokenId, Instant expiresAt) {
            blocked.add(tokenId);
        }

        @Override
        public boolean isBlocked(String tokenId) {
            return blocked.contains(tokenId);
        }
    }

    static class InMemoryLoginAttempts implements LoginAttemptPort {
        private final Map<String, Integer> failures = new ConcurrentHashMap<>();

        void clear() {
            failures.clear();
        }

        @Override
        public Optional<Duration> lockRemaining(String email, String clientIp) {
            return failures.getOrDefault(email, 0) >= 5 ? Optional.of(Duration.ofMinutes(15)) : Optional.empty();
        }

        @Override
        public void recordFailure(String email, String clientIp) {
            failures.merge(email, 1, Integer::sum);
        }

        @Override
        public void reset(String email, String clientIp) {
            failures.remove(email);
        }
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, GlobalExceptionHandler.class, ApplicationProperties.class})
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
        InMemoryAccessTokenBlocklist accessTokenBlocklist() {
            return new InMemoryAccessTokenBlocklist();
        }

        @Bean
        InMemoryLoginAttempts loginAttempts() {
            return new InMemoryLoginAttempts();
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
                LoginAttemptPort loginAttempts,
                Clock clock
        ) {
            return new AuthenticateUserUseCase(userRepository, refreshTokenRepository, tokenProvider, passwordEncoder, loginAttempts, clock);
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
                AccessTokenBlocklistPort accessTokenBlocklist,
                JwtTokenProviderAdapter tokenProvider,
                Clock clock
        ) {
            return new LogoutUseCase(refreshTokenRepository, accessTokenBlocklist, tokenProvider, clock);
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
