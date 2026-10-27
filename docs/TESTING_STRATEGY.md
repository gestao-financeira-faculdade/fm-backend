# Guia de Qualidade e Estratégia de Testes — Java 25 / Spring Boot 4

Referência operacional para agentes de IA e desenvolvedores ao projetar, criar, modificar ou revisar testes automatizados no repositório `gestao-financeira`. Complementa os guias [JAVA_BEST_PRACTICES.md](./JAVA_BEST_PRACTICES.md) e [HEXAGONAL_ARCHITECTURE.md](./HEXAGONAL_ARCHITECTURE.md).

- **Contexto tecnológico de testes do repositório (`pom.xml`):** Java 25, Spring Boot `4.1.1`, JUnit 5 (Jupiter), AssertJ, Mockito, `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-jooq-test`, `spring-boot-starter-flyway-test`, `spring-boot-starter-validation-test`, `spring-boot-starter-grpc-server-test`, `spring-boot-starter-grpc-client-test`, `spring-boot-starter-actuator-test`, `spring-boot-starter-opentelemetry-test`, `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql` (`PostgreSQLContainer`) e `testcontainers-grafana` (`LgtmStackContainer`).

## Índice

1. [Princípios Gerais e Regras Operacionais](#1-princípios-gerais-e-regras-operacionais)
2. [Camada 1 — Testes Unitários (Unit Tests)](#2-camada-1--testes-unitários-unit-tests)
   - [2.1 Objetivo e escopo de cobertura](#21-objetivo-e-escopo-de-cobertura)
   - [2.2 O que NÃO testar com testes unitários](#22-o-que-não-testar-com-testes-unitários)
   - [2.3 Boas práticas de implementação](#23-boas-práticas-de-implementação)
   - [2.4 Exemplos de referência (Domínio e Caso de Uso)](#24-exemplos-de-referência-domínio-e-caso-de-uso)
3. [Camada 2 — Testes de Integração (Integration Tests)](#3-camada-2--testes-de-integração-integration-tests)
   - [3.1 Objetivo e escopo de cobertura nas fronteiras](#31-objetivo-e-escopo-de-cobertura-nas-fronteiras)
   - [3.2 Boas práticas de implementação](#32-boas-práticas-de-implementação)
   - [3.3 Exemplos de referência (WebMVC, Persistência JPA/jOOQ e Concorrência)](#33-exemplos-de-referência-webmvc-persistência-jpajooq-e-concorrência)
4. [Camada 3 — Testes End-to-End / E2E (System & Acceptance Tests)](#4-camada-3--testes-end-to-end--e2e-system--acceptance-tests)
   - [4.1 Objetivo e escopo de cobertura](#41-objetivo-e-escopo-de-cobertura)
   - [4.2 Boas práticas de implementação](#42-boas-práticas-de-implementação)
   - [4.3 Exemplo de referência (Fluxo Financeiro Ponta a Ponta)](#43-exemplo-de-referência-fluxo-financeiro-ponta-a-ponta)
5. [Matriz Comparativa e Critérios de Decisão para o Agente](#5-matriz-comparativa-e-critérios-de-decisão-para-o-agente)
6. [Anti-patterns de Testes](#6-anti-patterns-de-testes)
   - [QA-01 Contexto Spring em testes de domínio ou casos de uso](#qa-01-contexto-spring-em-testes-de-domínio-ou-casos-de-uso)
   - [QA-02 Mock de `JpaRepository`, `EntityManager` ou `DSLContext` em adaptadores de persistência](#qa-02-mock-de-jparepository-entitymanager-ou-dslcontext-em-adaptadores-de-persistência)
   - [QA-03 Imprecisão monetária e igualdade incorreta de `BigDecimal` em asserções](#qa-03-imprecisão-monetária-e-igualdade-incorreta-de-bigdecimal-em-asserções)
   - [QA-04 Não determinismo temporal e sincronização com `Thread.sleep`](#qa-04-não-determinismo-temporal-e-sincronização-com-threadsleep)
   - [QA-05 Acoplamento excessivo a detalhes internos via `verify` (Over-mocking)](#qa-05-acoplamento-excessivo-a-detalhes-internos-via-verify-over-mocking)
   - [QA-06 Poluição de estado compartilhado entre testes de integração/E2E](#qa-06-poluição-de-estado-compartilhado-entre-testes-de-integraçãoe2e)
   - [QA-07 Instanciação manual de múltiplos contêineres Docker sem reuso](#qa-07-instanciação-manual-de-múltiplos-contêineres-docker-sem-reuso)
   - [QA-08 Pirâmide invertida (*Ice Cream Cone*) com excesso de testes E2E](#qa-08-pirâmide-invertida-ice-cream-cone-com-excesso-de-testes-e2e)
7. [Convenções de Nomenclatura e Execução](#7-convenções-de-nomenclatura-e-execução)

---

## 1. Princípios Gerais e Regras Operacionais

1. **Alinhamento com a Arquitetura Hexagonal:** a estratégia de testes reflete a separação entre `domain`, `application` e `adapter` definida em [HEXAGONAL_ARCHITECTURE.md](./HEXAGONAL_ARCHITECTURE.md). O núcleo de negócio é testado em memória sem frameworks; as fronteiras de entrada e saída são testadas com *slice tests* dedicados; o sistema integrado é validado por testes E2E cirúrgicos.
2. **Rigor Financeiro Inegociável:** em um sistema de gestão financeira, erros de arredondamento, condições de corrida em saldos, violação de idempotência ou perda de precisão em `BigDecimal` são falhas críticas. Todo cálculo monetário deve ser validado em valores limites (zero, um centavo, dízimas periódicas em rateios/parcelas, limites máximos e saldos insuficientes).
3. **Feedback Rápido e Determinismo:** testes não podem depender de relógio do sistema (`Instant.now()`), ordem de execução de métodos, rede externa pública ou `Thread.sleep(...)`.
4. **Fidelidade de Infraestrutura nas Fronteiras:** nunca substitua o PostgreSQL por bancos em memória (como H2 ou HSQLDB) nem mocke frameworks de persistência ao testar adaptadores de banco de dados. Utilize sempre o `TestcontainersConfiguration` (`postgres:latest` e `grafana/otel-lgtm:latest` via `@ServiceConnection`).
5. **Preservação do Escopo:** ao criar ou corrigir testes em uma tarefa, não adicione novas bibliotecas ao `pom.xml` sem solicitação explícita e não refatore código de produção fora do escopo.

---

## 2. Camada 1 — Testes Unitários (Unit Tests)

### 2.1 Objetivo e escopo de cobertura

Validar a lógica de negócio pura e a orquestração da aplicação com **execução em milissegundos**, **zero I/O** e **zero inicialização do Spring Framework**.

- **Entidades e Agregados de Domínio (`domain`):**
  - Transições de estado válidas e inválidas (ex.: `Account.debit(...)`, `Account.credit(...)`, bloqueio/encerramento de conta, estorno de lançamento).
  - Proteção de invariantes de negócio e lançamento de exceções de domínio específicas (ex.: `InsufficientBalanceException`, `ClosedAccountException`) com validação dos dados contextuais da exceção.
- **Objetos de Valor (`domain`):**
  - Validação no construtor compacto de `records` (ex.: `Money`, `InstallmentPlan`, `TransactionPeriod`, `TaxRate`).
  - Operações aritméticas imutáveis em `Money` (soma, subtração, multiplicação por fator, divisão/rateio com `RoundingMode` explícito e distribuição de resíduo de centavos).
  - Semântica de valor e comparação numérica consistente.
- **Serviços de Domínio (`domain`):**
  - Regras *stateless* que coordenam múltiplos agregados ou políticas complexas de cálculo (juros, multas, conciliação de partidas dobradas, conversão cambial).
- **Casos de Uso Isolados (`application`):**
  - Fluxo de orquestração do `@Service` de aplicação instanciado diretamente via construtor (`new TransferMoneyUseCase(...)`).
  - Coordenação entre busca nas portas de saída, chamada aos métodos ricos do domínio, persistência do estado atualizado e retorno de resultados tipados (`record` ou `sealed interface` `*Outcome`).
  - Uso preferencial de **In-Memory Fakes** (ex.: `InMemoryAccountRepository` baseado em `Map<UUID, Account>`) para portas de repositório e *Test Doubles* (Spies/Mocks com Mockito) apenas para portas de efeitos colaterais externos (ex.: publicação de notificações ou chamadas a gateways externos).

### 2.2 O que NÃO testar com testes unitários

- **Não suba o contexto do Spring (`@SpringBootTest`, `@ExtendWith(SpringExtension.class)`):** classes de `domain` e `application` devem ser instanciadas diretamente com `new`.
- **Não teste mapeamentos SQL, JPQL, entidades `@Entity` JPA ou queries jOOQ:** isso pertence aos testes de integração com PostgreSQL real.
- **Não teste serialização JSON (Jackson), rotas HTTP (`@GetMapping`/`@PostMapping`) ou contratos Protobuf/gRPC:** isso pertence aos testes de integração da camada `adapter.in`.
- **Não teste getters/setters triviais, construtores gerados pelo compilador sem validação ou código de infraestrutura de terceiros.**

### 2.3 Boas práticas de implementação

1. **JUnit 5 + AssertJ puros:** utilize asserções fluentes do AssertJ (`assertThat(...)`, `assertThatThrownBy(...)`) em vez de `Assertions.assertEquals` básico, garantindo mensagens de falha descritivas.
2. **Comparação correta de `BigDecimal`:**
   - Para validar valor numérico independente de escala (`10.0` vs `10.00`), use `assertThat(actual).isEqualByComparingTo("10.00")`.
   - Quando a regra exigir escala monetária estrita (ex.: sempre 2 casas decimais em moeda corrente), valide tanto `isEqualByComparingTo` quanto `assertThat(actual.scale()).isEqualTo(2)`.
3. **Determinismo temporal absoluto (`java.time.Clock`):**
   - Nunca use `Instant.now()` ou `LocalDate.now()` soltos. Passe `Clock.fixed(Instant.parse("2026-03-15T14:30:00Z"), ZoneOffset.UTC)` no construtor do caso de uso ou serviço de domínio para testar vencimentos, juros por atraso, fechamento de fatura e janelas de competência.
4. **Testes Parametrizados (`@ParameterizedTest`):**
   - Utilize `@CsvSource` ou `@MethodSource` para exercitar tabelas de bordas financeiras: valores negativos, zero, `0.01`, limites de transação, divisões em 3/6/7/12 parcelas com `RoundingMode.HALF_EVEN` e verificação de que a soma das parcelas bate exatamente com o valor total.
5. **Padrão Arrange-Act-Assert (AAA) / Given-When-Then:**
   - Separe visualmente a preparação do cenário (*Arrange*), a execução da ação única (*Act*) e a verificação do estado/resultado (*Assert*).
6. **Preferência por State Verification (Fakes) sobre Behavior Verification (Mocks):**
   - Para portas de repositório (`AccountRepositoryPort`), prefira um *Fake* em memória leve e reutilizável no pacote de teste. Verifique o estado final salvo no Fake (`assertThat(accountRepository.findById(id)).contains(...)`) em vez de acoplar o teste a chamadas `verify(mockRepo, times(1)).save(any())`.

### 2.4 Exemplos de referência (Domínio e Caso de Uso)

```java
package com.emanudev.gestaofinanceira.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest(name = "{0} / {1} deve resultar em {2} (HALF_EVEN)")
    @CsvSource({
        "100.00, 3, 33.33",
        "10.00,  6, 1.67",
        "0.05,   2, 0.02",
        "0.07,   2, 0.04"
    })
    void shouldDivideWithBankersRounding(String amount, int divisor, String expected) {
        // Arrange
        var money = new Money(new BigDecimal(amount));

        // Act
        Money result = money.divide(divisor, RoundingMode.HALF_EVEN);

        // Assert
        assertThat(result.amount())
                .isEqualByComparingTo(expected)
                .extracting(BigDecimal::scale)
                .isEqualTo(2);
    }

    @Test
    void shouldRejectNullAmount() {
        assertThatThrownBy(() -> new Money(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("amount");
    }
}
```

```java
package com.emanudev.gestaofinanceira.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.emanudev.gestaofinanceira.account.domain.Account;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferMoneyUseCaseTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-10T12:00:00Z");

    private InMemoryAccountRepository accountRepository;
    private TransferMoneyUseCase useCase;

    @BeforeEach
    void setUp() {
        accountRepository = new InMemoryAccountRepository();
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        useCase = new TransferMoneyUseCase(accountRepository, fixedClock);
    }

    @Test
    void shouldDebitSourceAndCreditTargetWhenBalanceIsSufficient() {
        // Arrange
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        accountRepository.save(new Account(sourceId, new Money(new BigDecimal("500.00"))));
        accountRepository.save(new Account(targetId, new Money(new BigDecimal("100.00"))));

        var command = new TransferMoneyCommand(sourceId, targetId, new Money(new BigDecimal("150.00")));

        // Act
        TransferOutcome outcome = useCase.execute(command);

        // Assert
        assertThat(outcome).isInstanceOf(TransferOutcome.Completed.class);
        assertThat(accountRepository.getById(sourceId).balance().amount())
                .isEqualByComparingTo("350.00");
        assertThat(accountRepository.getById(targetId).balance().amount())
                .isEqualByComparingTo("250.00");
    }

    private static final class InMemoryAccountRepository implements AccountRepositoryPort {
        private final Map<UUID, Account> store = new HashMap<>();

        @Override
        public Optional<Account> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public void save(Account account) {
            store.put(account.id(), account);
        }

        Account getById(UUID id) {
            return store.get(id);
        }
    }
}
```

---

## 3. Camada 2 — Testes de Integração (Integration Tests)

### 3.1 Objetivo e escopo de cobertura nas fronteiras

Validar que os **Adaptadores de Entrada (`adapter.in`)** e os **Adaptadores de Saída (`adapter.out`)** integram corretamente o núcleo da aplicação aos frameworks de transporte, serialização e persistência reais, subindo apenas a fatia (*slice*) necessária do Spring Boot.

1. **Adaptadores de Entrada REST (`adapter.in.web`):**
   - Teste com `@WebMvcTest(controllers = ...)` e `MockMvc` / `MockMvcTester`.
   - Cobertura de: roteamento HTTP, códigos de status (`200`, `201`, `400`, `404`, `409`, `422`), serialização/desserialização JSON (formato ISO-8601 de datas, precisão numérica de `BigDecimal`), Bean Validation (`@Valid`, `@NotNull`, `@Positive`) e tradução de `Outcome` ou exceções de domínio para RFC 9457 (`ProblemDetail`) via `@RestControllerAdvice`.
   - O caso de uso (`*UseCase`) é substituído por `@MockitoBean`. O banco de dados não é inicializado.
2. **Adaptadores de Entrada gRPC (`adapter.in.grpc`):**
   - Teste de fatia gRPC (`spring-boot-starter-grpc-server-test` / canal in-process) validando a conversão de mensagens Protobuf para `*Command`/`*Query`, resposta Protobuf e mapeamento de erros de domínio para códigos `io.grpc.Status` (`INVALID_ARGUMENT`, `NOT_FOUND`, `FAILED_PRECONDITION`, `ALREADY_EXISTS`).
3. **Adaptadores de Saída de Persistência (`adapter.out.persistence`):**
   - Teste com `@DataJpaTest` ou `@JooqTest`, importando o adaptador concreto (`@Import({TestcontainersConfiguration.class, AccountPersistenceAdapter.class})`).
   - Execução contra **PostgreSQL real** provido por `TestcontainersConfiguration` (`postgres:latest`) com todas as migrações **Flyway** aplicadas automaticamente.
   - Cobertura de: mapeamento entre modelo de domínio e `*JpaEntity`, queries customizadas JPQL/SQL nativo, queries tipadas e projeções de leitura com `DSLContext` (jOOQ), integridade referencial (`FOREIGN KEY`), constraints `UNIQUE` (ex.: chaves de idempotência), `CHECK` constraints (ex.: saldo ou valor positivo no banco) e tipos específicos do PostgreSQL (`UUID`, `NUMERIC(19,2)`, `TIMESTAMPTZ`, `JSONB`).
4. **Concorrência e Bloqueios no Banco de Dados:**
   - Validação de **Optimistic Locking (`@Version`)** garantindo lançamento e tradução de `ObjectOptimisticLockingFailureException` quando duas transações concorrentes tentam atualizar o mesmo agregado financeiro.
   - Validação de **Pessimistic Locking (`SELECT ... FOR UPDATE` / `LockModeType.PESSIMISTIC_WRITE`)** ou constraints de unicidade sob múltiplas threads (`ExecutorService` com Virtual Threads / `CountDownLatch`) usando transações reais independentes (`TransactionTemplate`).
5. **Adaptadores de Saída para Clientes Externos (`adapter.out.client`):**
   - Para clientes HTTP (`RestClient`), testar serialização de request, cabeçalhos, timeouts e tratamento de erros (`4xx`/`5xx`) com `@RestClientTest` e `MockRestServiceServer`.
   - Para clientes gRPC externos, testar com servidor gRPC local in-process (`spring-boot-starter-grpc-client-test`).

### 3.2 Boas práticas de implementação

1. **Reuso do `TestcontainersConfiguration` com `@ServiceConnection`:**
   - Sempre anote testes de persistência com `@Import(TestcontainersConfiguration.class)` (ou uma configuração específica caso queira apenas o bean `PostgreSQLContainer` em *slice tests* de banco). O `@ServiceConnection` configura automaticamente `spring.datasource.*` e integra o Flyway sem necessidade de `@DynamicPropertySource` manual.
2. **Proibição de mockar `JpaRepository`, `EntityManager` ou `DSLContext` (Regra [HEX-16](./HEXAGONAL_ARCHITECTURE.md#hex-16-testes-baseados-exclusivamente-em-mocks-nos-adaptadores)):**
   - Um teste de `*PersistenceAdapter` que mocka o repositório Spring Data ou o jOOQ não testa o SQL nem o mapeamento JPA. Use sempre o banco PostgreSQL do Testcontainers.
3. **Forçar `flush()` e `clear()` em testes JPA:**
   - Em testes `@DataJpaTest`, chamar apenas `adapter.save(domainObj)` seguido de `adapter.findById(id)` pode retornar a instância em cache de primeiro nível do Hibernate (`PersistenceContext`) sem sequer ter executado o `INSERT`/`UPDATE` no PostgreSQL ou validado o `SELECT` de leitura.
   - Injete `TestEntityManager` (ou `EntityManager`) e chame `entityManager.flush()` e `entityManager.clear()` antes de buscar novamente ou verificar constraints SQL.
4. **Isolamento de Dados entre Testes:**
   - Em `@DataJpaTest` e `@JooqTest`, o Spring faz rollback automático da transação ao final de cada método `@Test`.
   - Quando o teste testar explicitamente concorrência multi-thread ou `TransactionTemplate` com commits reais (`PROPAGATION_REQUIRES_NEW`), limpe os registros criados (`@AfterEach` ou `TRUNCATE ... CASCADE`) ou utilize identificadores (`UUID.randomUUID()`) totalmente isolados por teste.

### 3.3 Exemplos de referência (WebMVC, Persistência JPA/jOOQ e Concorrência)

```java
package com.emanudev.gestaofinanceira.account.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emanudev.gestaofinanceira.account.application.DebitAccountCommand;
import com.emanudev.gestaofinanceira.account.application.DebitAccountUseCase;
import com.emanudev.gestaofinanceira.account.domain.InsufficientBalanceException;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AccountController.class)
class AccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DebitAccountUseCase debitAccountUseCase;

    @Test
    void shouldReturnUnprocessableEntityProblemDetailWhenBalanceIsInsufficient() throws Exception {
        // Arrange
        UUID accountId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        var amount = new Money(new BigDecimal("250.00"));
        var currentBalance = new Money(new BigDecimal("100.00"));

        given(debitAccountUseCase.execute(new DebitAccountCommand(accountId, amount)))
                .willThrow(new InsufficientBalanceException(accountId, amount, currentBalance));

        // Act & Assert (em @WebMvcTest sem context-path no MockMvc, usa-se a rota do controller "/v1/..."; em chamadas HTTP reais contra o container web, usa-se "/api/v1/...")
        mockMvc.perform(post("/v1/accounts/{id}/debits", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 250.00
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Insufficient Balance"))
                .andExpect(jsonPath("$.status").value(422));
    }
}
```

```java
package com.emanudev.gestaofinanceira.account.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.emanudev.gestaofinanceira.TestcontainersConfiguration;
import com.emanudev.gestaofinanceira.account.domain.Account;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({TestcontainersConfiguration.class, AccountPersistenceAdapter.class})
class AccountPersistenceAdapterIntegrationTest {

    @Autowired
    private AccountPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistAndReloadAccountFromRealPostgreSql() {
        // Arrange
        UUID id = UUID.randomUUID();
        var account = new Account(id, new Money(new BigDecimal("1250.75")));

        // Act
        adapter.save(account);
        entityManager.flush();
        entityManager.clear(); // Garante SELECT real no PostgreSQL em vez do cache de 1º nível

        Optional<Account> loaded = adapter.findById(id);

        // Assert
        assertThat(loaded).isPresent();
        assertThat(loaded.get().id()).isEqualTo(id);
        assertThat(loaded.get().balance().amount()).isEqualByComparingTo("1250.75");
    }
}
```

---

## 4. Camada 3 — Testes End-to-End / E2E (System & Acceptance Tests)

### 4.1 Objetivo e escopo de cobertura

Validar os **fluxos críticos de negócio ponta a ponta** com o contexto completo da aplicação (`@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`) e infraestrutura real em contêineres via `@Import(TestcontainersConfiguration.class)` (`PostgreSQLContainer` + `LgtmStackContainer`).

Um teste E2E exercita todas as camadas integradas sem mocks internos:
1. **Entrada Real via Rede Local:** requisição HTTP (cliente REST de teste) ou chamada via stub cliente gRPC.
2. **Validação e Observabilidade:** filtros HTTP/interceptors gRPC, geração de spans/métricas OpenTelemetry/Micrometer (`datasource-micrometer-opentelemetry` e `LgtmStackContainer`).
3. **Caso de Uso e Transação Real:** execução do `@Service` de aplicação dentro de uma transação `@Transactional` real gerenciada pelo Spring.
4. **Persistência e Efeitos Colaterais:** gravação nas tabelas do PostgreSQL (criadas pelo Flyway), disparo de listeners transacionais (`@TransactionalEventListener(phase = AFTER_COMMIT)`) e verificação da leitura subsequente (ex.: consulta via endpoint GET ou query jOOQ confirmando o novo saldo e o extrato gerado).

**Cenários que justificam testes E2E neste projeto:**
- **Smoke Test de Composição (`GestaoFinanceiraApplicationTests`):** garante que todos os beans do Spring Boot 4, migrações Flyway, conexões PostgreSQL e exportadores OpenTelemetry/LGTM sobem sem erros de configuração.
- **Jornadas Financeiras Críticas (*Golden Paths*):**
  - Criação de conta -> Depósito inicial -> Transferência entre contas -> Consulta de saldo e extrato consolidado.
  - Processamento de lançamento com chave de idempotência (`Idempotency-Key`): envio duplicado da mesma requisição garantindo que o débito ocorra **apenas uma vez** no banco de dados e retorne a mesma resposta.
  - Rollback transacional ponta a ponta: falha induzida na etapa final de um fluxo garantindo que nenhum registro parcial permaneça comitado no PostgreSQL.

### 4.2 Boas práticas de implementação

1. **Seletividade e Equilíbrio da Pirâmide:**
   - **Não replique matrizes exaustivas de validação nos testes E2E.** Deixe as 15 variações de bordas de `BigDecimal` para os testes unitários (`@ParameterizedTest`) e as validações de payload inválido (`400 Bad Request`) para os testes `@WebMvcTest`. Nos testes E2E, foque no caminho feliz principal, na garantia transacional e na idempotência ponta a ponta.
2. **Isolamento sem Rollback Automático do Teste:**
   - Em testes com `WebEnvironment.RANDOM_PORT`, o servidor HTTP/gRPC processa a requisição em outra thread e **faz commit real no PostgreSQL**. Portanto, anotar o método `@Test` com `@Transactional` **não** reverterá os dados gravados pela chamada HTTP.
   - Crie os dados de cada teste usando `UUID.randomUUID()` exclusivos (contas e chaves de idempotência próprias por teste) para que os testes sejam totalmente independentes e nunca colidam entre si.
3. **Preparação de Cenários Realistas:**
   - Prepare o estado inicial através das próprias APIs/casos de uso da aplicação ou de um helper de banco explícito, e verifique o resultado tanto na resposta do protocolo (HTTP/gRPC) quanto no estado persistido/consultado.
4. **Reuso do Contexto Spring (Context Caching):**
   - Mantenha a mesma configuração de anotações (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Import(TestcontainersConfiguration.class)`) entre as classes de teste E2E (preferencialmente herdando de uma classe base ou anotação composta `@IntegrationE2ETest`). Evite usar `@DirtiesContext` ou `@MockitoBean` em testes E2E, pois isso invalida o cache do `ApplicationContext` do Spring e multiplica o tempo de build.

### 4.3 Exemplo de referência (Fluxo Financeiro Ponta a Ponta)

```java
package com.emanudev.gestaofinanceira.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.emanudev.gestaofinanceira.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferMoneyE2ETest {

    @LocalServerPort
    private int port;

    @Test
    void shouldExecuteIdempotentTransferEndToEnd(@Autowired RestClient.Builder restClientBuilder) {
        // Arrange
        RestClient client = restClientBuilder.baseUrl("http://localhost:" + port).build();
        UUID idempotencyKey = UUID.randomUUID();

        UUID sourceAccountId = createAccountWithBalance(client, "1000.00");
        UUID targetAccountId = createAccountWithBalance(client, "200.00");

        String payload = """
                {
                  "sourceAccountId": "%s",
                  "targetAccountId": "%s",
                  "amount": 300.00
                }
                """.formatted(sourceAccountId, targetAccountId);

        // Act — 1ª requisição e 2ª requisição idêntica (retry de rede com mesma Idempotency-Key)
        ResponseEntity<Void> firstResponse = client.post()
                .uri("/api/v1/transfers")
                .header("Idempotency-Key", idempotencyKey.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        ResponseEntity<Void> retryResponse = client.post()
                .uri("/api/v1/transfers")
                .header("Idempotency-Key", idempotencyKey.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        // Assert — transferência aplicada exatamente uma única vez no PostgreSQL
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetchBalance(client, sourceAccountId)).isEqualByComparingTo("700.00");
        assertThat(fetchBalance(client, targetAccountId)).isEqualByComparingTo("500.00");
    }

    private UUID createAccountWithBalance(RestClient client, String initialBalance) {
        // Helper de preparação via API
        return UUID.randomUUID();
    }

    private BigDecimal fetchBalance(RestClient client, UUID accountId) {
        // Helper de consulta de saldo via API GET
        return new BigDecimal("700.00");
    }
}
```

---

## 5. Matriz Comparativa e Critérios de Decisão para o Agente

Use a matriz abaixo para decidir rapidamente **qual tipo de teste criar ou atualizar** em cada tarefa:

| Camada de Teste | Alvo Arquitetural | Ferramentas e Anotações | Infraestrutura / Contexto | Tempo Esperado por Teste | Quando Criar / Atualizar (Critério de Decisão) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1. Unitário (Domínio)** | `*.domain.*` (Entidades, Agregados, Value Objects, Domain Services) | JUnit 5 (`@Test`, `@ParameterizedTest`), AssertJ, `Clock.fixed(...)` | **Zero Spring, Zero Mocks, Zero Docker** | `< 5 ms` | **Sempre** que criar ou alterar regras de negócio, invariantes, validações de estado ou cálculos com `BigDecimal`. |
| **1. Unitário (Aplicação)** | `*.application.*` (`*UseCase`, `*Command`, `*Outcome`) | JUnit 5, AssertJ, *In-Memory Fakes* (`Map`), Mockito pontual | **Zero Spring, Zero Docker** (instanciação via `new`) | `< 15 ms` | **Sempre** que criar ou alterar um caso de uso, fluxos alternativos de orquestração ou interações com portas de saída. |
| **2. Integração (Entrada Web/gRPC)** | `*.adapter.in.web.*`<br>`*.adapter.in.grpc.*` | `@WebMvcTest`, `MockMvc`, `@MockitoBean` (ou teste in-process gRPC) | Fatia Web/gRPC do Spring (**sem banco de dados**) | `50 ms – 300 ms` (após subida da fatia) | **Sempre** que criar ou alterar endpoints REST, serviços gRPC, validações `@Valid`, contratos JSON/Protobuf ou `@RestControllerAdvice`. |
| **2. Integração (Saída Persistência)** | `*.adapter.out.persistence.*` (`*PersistenceAdapter`, JPA, jOOQ, Flyway) | `@DataJpaTest` ou `@JooqTest` + `@Import(TestcontainersConfiguration.class)` | Fatia de dados do Spring + **PostgreSQL real (Testcontainers)** | `50 ms – 400 ms` (contêiner reutilizado) | **Sempre** que criar ou alterar migrações Flyway (`V*__*.sql`), `@Entity`, repositórios JPA, queries jOOQ ou locks de concorrência. |
| **2. Integração (Saída Cliente)** | `*.adapter.out.client.*` (Clientes HTTP / gRPC externos) | `@RestClientTest` + `MockRestServiceServer` ou servidor gRPC local | Fatia de cliente HTTP/gRPC (**sem rede externa real**) | `30 ms – 200 ms` | **Sempre** que integrar com APIs externas, validando payloads enviados, timeouts e tratamento de erros `4xx`/`5xx`. |
| **3. End-to-End (E2E / Sistema)** | Fluxo completo (`adapter.in` -> `application` -> `domain` -> `adapter.out`) | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Import(TestcontainersConfiguration.class)` | Contexto Spring completo + **PostgreSQL + Grafana LGTM (Testcontainers)** | `200 ms – 1.5 s` | Para **jornadas financeiras críticas**, garantias transacionais ponta a ponta, idempotência e smoke test de subida do contexto. |

---

## 6. Anti-patterns de Testes

Ao revisar código de teste, classifique e reporte os problemas abaixo com a prioridade indicada:

### QA-01 Contexto Spring em testes de domínio ou casos de uso

- **Problema:** anotar testes de classes de `domain` ou `application` com `@SpringBootTest` ou `@ExtendWith(SpringExtension.class)`.
- **Consequência:** transforma testes que deveriam rodar em `5 ms` em testes de vários segundos e mascara acoplamentos indevidos de injeção de dependência.
- **Correção:** instancie entidades, objetos de valor e casos de uso diretamente pelo construtor com `new` (alinha-se a [HEX-15](./HEXAGONAL_ARCHITECTURE.md#hex-15-testes-de-unidade-que-exigem-infraestrutura-real) e [TST-02](./JAVA_BEST_PRACTICES.md#tst-02-testes-frágeis-lentos-ou-acoplados-à-implementação)).
- **Prioridade:** Alta.

### QA-02 Mock de `JpaRepository`, `EntityManager` ou `DSLContext` em adaptadores de persistência

- **Problema:** usar `@Mock SpringDataAccountRepository` ou `@Mock DSLContext` para testar classes `*PersistenceAdapter`.
- **Consequência:** falsos positivos graves — o teste passa, mas a query SQL/jOOQ tem erro de sintaxe, o mapeamento JPA falha ou uma constraint da migração Flyway quebra no PostgreSQL.
- **Correção:** use `@DataJpaTest` ou `@JooqTest` com `@Import(TestcontainersConfiguration.class)` contra o PostgreSQL real (alinha-se a [HEX-16](./HEXAGONAL_ARCHITECTURE.md#hex-16-testes-baseados-exclusivamente-em-mocks-nos-adaptadores)).
- **Prioridade:** Alta.

### QA-03 Imprecisão monetária e igualdade incorreta de `BigDecimal` em asserções

- **Problema:** construir `BigDecimal` com `double` (`new BigDecimal(10.55)`) nos testes ou comparar valores monetários com `assertEquals(new BigDecimal("10.0"), actual)` / `assertThat(actual).isEqualTo(...)` sem considerar a escala.
- **Consequência:** `BigDecimal.equals` compara valor **e** escala (`10.0` != `10.00`), gerando falhas espúrias ou ocultando erros de arredondamento.
- **Correção:** instancie sempre com `String` (`new BigDecimal("10.55")`) e valide o valor numérico com `assertThat(actual).isEqualByComparingTo("10.55")` (alinha-se a [COR-01](./JAVA_BEST_PRACTICES.md#cor-01-uso-de-floatdouble-ou-bigdecimal-incorreto-para-valores-monetários)).
- **Prioridade:** Crítica.

### QA-04 Não determinismo temporal e sincronização com `Thread.sleep`

- **Problema:** usar `Instant.now()` / `LocalDate.now()` no código ou no teste sem relógio fixo, ou usar `Thread.sleep(...)` para aguardar processamento assíncrono/concorrente.
- **Consequência:** *flaky tests* (testes que falham próximo à meia-noite, em fins de mês/anos bissextos ou em máquinas de CI sob carga).
- **Correção:** injete `Clock.fixed(...)` nos testes unitários e use sincronizadores explícitos (`CountDownLatch`, `CyclicBarrier`, `CompletableFuture.get(timeout)`) em testes de concorrência.
- **Prioridade:** Alta.

### QA-05 Acoplamento excessivo a detalhes internos via `verify` (Over-mocking)

- **Problema:** verificar a sequência exata de chamadas internas (`verifyNoMoreInteractions`, `InOrder`) para repositórios e colaboradores internos quando o que importa é o resultado retornado e o estado persistido.
- **Consequência:** testes frágeis que quebram em qualquer refatoração interna legítima mesmo quando o comportamento externo permanece idêntico.
- **Correção:** prefira *In-Memory Fakes* para repositórios nos testes de casos de uso e valide o estado final resultante; reserve `verify(...)` para portas de saída puramente baseadas em comando sem retorno consultável (ex.: disparo de evento/notificação externa).
- **Prioridade:** Média.

### QA-06 Poluição de estado compartilhado entre testes de integração/E2E

- **Problema:** usar IDs fixos hardcoded (ex.: `UUID.fromString("00000000-0000-0000-0000-000000000001")`) em múltiplos testes E2E que fazem commit real no banco sem limpeza, ou depender da ordem de execução (`@TestMethodOrder`) para que um teste aproveite os dados criados pelo teste anterior.
- **Consequência:** testes passam quando executados isoladamente na IDE, mas falham por violação de chave única ou saldo divergente quando executados em conjunto pela suíte Maven.
- **Correção:** cada método `@Test` deve ser 100% autossuficiente, criando seus próprios recursos com `UUID.randomUUID()` ou limpando o estado ao final.
- **Prioridade:** Alta.

### QA-07 Instanciação manual de múltiplos contêineres Docker sem reuso

- **Problema:** declarar `new PostgreSQLContainer<>(...)` manualmente com `@Container` em cada classe de teste sem `@ServiceConnection` ou alterar as propriedades do `ApplicationContext` (`@MockitoBean`, `@TestPropertySource` distintos) em dezenas de classes de integração.
- **Consequência:** dezenas de contêineres PostgreSQL iniciados e destruídos durante o build, esgotando memória e aumentando o tempo da suíte de segundos para vários minutos.
- **Correção:** utilize sempre `@Import(TestcontainersConfiguration.class)` para reaproveitar a configuração centralizada de `PostgreSQLContainer` e `LgtmStackContainer` via `@ServiceConnection`.
- **Prioridade:** Média.

### QA-08 Pirâmide invertida (*Ice Cream Cone*) com excesso de testes E2E

- **Problema:** testar todas as permutações de regras de negócio, validações de campos nulos e cálculos de juros subindo `@SpringBootTest` e fazendo requisições HTTP completas.
- **Consequência:** suíte de testes extremamente lenta, difícil de diagnosticar quando falha e com baixa cobertura das bordas reais de domínio.
- **Correção:** desça os testes de permutação de regras para a **Camada 1 (Unitários)**, os testes de contrato HTTP/SQL para a **Camada 2 (Integração)** e mantenha na **Camada 3 (E2E)** apenas os fluxos integrados essenciais.
- **Prioridade:** Média.

---

## 7. Convenções de Nomenclatura e Execução

1. **Nomenclatura das Classes de Teste:**
   - **Testes Unitários:** `<ClasseAlvo>Test.java` (ex.: `MoneyTest.java`, `AccountTest.java`, `TransferMoneyUseCaseTest.java`).
   - **Testes de Integração de Fronteira:** `<ClasseAlvo>IntegrationTest.java` (ex.: `AccountControllerIntegrationTest.java`, `AccountPersistenceAdapterIntegrationTest.java`).
   - **Testes End-to-End:** `<FluxoOuRecurso>E2ETest.java` (ex.: `TransferMoneyE2ETest.java`) e `<Aplicacao>ApplicationTests.java` para o smoke test de bootstrap.
2. **Nomenclatura dos Métodos de Teste:**
   - Utilize nomes descritivos em inglês no padrão `should<ExpectedBehavior>When<Condition>()` (ex.: `shouldThrowInsufficientBalanceWhenDebitExceedsAvailableBalance()`).
   - Em `@ParameterizedTest`, utilize o atributo `name` para deixar o relatório de execução claro (ex.: `@ParameterizedTest(name = "amount={0} divided by {1} should be {2}")`).
3. **Visibilidade Package-Private (JUnit 5):**
   - Classes e métodos de teste no JUnit 5 **não** devem usar o modificador `public` (declare `class AccountTest` e `void should...()`).
4. **Execução via Maven Wrapper:**
   - Executar toda a suíte: `./mvnw test` (Linux/macOS) ou `.\mvnw.cmd test` (Windows).
   - Executar apenas uma classe de teste específica durante o desenvolvimento: `.\mvnw.cmd test -Dtest=AccountTest`.
   - Executar apenas testes unitários rápidos por padrão de nome: `.\mvnw.cmd test -Dtest="*Test,!*IntegrationTest,!*E2ETest,!*ApplicationTests"`.
