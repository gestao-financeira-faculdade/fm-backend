# Guia de boas práticas e anti-patterns Java 25 (Spring Boot)

Guia de referência operacional para agentes de programação (incluindo o Antigravity) ao criar, ler, modificar, refatorar ou revisar código Java neste repositório.

- **Escopo:** código Java (`*.java`) e decisões de implementação na stack do projeto (Java 25, Spring Boot 4, Spring WebMVC, Spring Data JPA/Hibernate, jOOQ, gRPC, Bean Validation, Micrometer/OpenTelemetry, Testcontainers e GraalVM Native Image).
- **Versão de referência:** Java SE 25 (JDK 25, GA em setembro de 2025).

## Índice

1. [Regras de decisão para o agente](#1-regras-de-decisão-para-o-agente)
2. [Convenções deste guia](#2-convenções-deste-guia)
3. [Compatibilidade e configuração do projeto](#3-compatibilidade-e-configuração-do-projeto)
4. [Recursos modernos do Java aplicados ao Spring](#4-recursos-modernos-do-java-aplicados-ao-spring)
   - [4.1 Modelagem de tipos e dados](#41-modelagem-de-tipos-e-dados)
   - [4.2 Pattern matching e switch](#42-pattern-matching-e-switch)
   - [4.3 Strings, datas e valores monetários](#43-strings-datas-e-valores-monetários)
   - [4.4 Coleções, streams e Optional](#44-coleções-streams-e-optional)
   - [4.5 Concorrência e Virtual Threads no Spring](#45-concorrência-e-virtual-threads-no-spring)
   - [4.6 Recursos em prévia, incubação ou desaconselhados](#46-recursos-em-prévia-incubação-ou-desaconselhados)
5. [Anti-patterns](#5-anti-patterns)
   - [5.1 Segurança](#51-segurança)
   - [5.2 Correção e persistência](#52-correção-e-persistência)
   - [5.3 Concorrência e estado em beans Spring](#53-concorrência-e-estado-em-beans-spring)
   - [5.4 Design e manutenibilidade](#54-design-e-manutenibilidade)
   - [5.5 Desempenho e acesso a dados](#55-desempenho-e-acesso-a-dados)
   - [5.6 Testes](#56-testes)
6. [Referências](#6-referências)

## 1. Regras de decisão para o agente

1. Consulte este guia antes de criar, modificar, refatorar ou revisar arquivos Java. Leia apenas as seções pertinentes à tarefa.
2. Aplique somente as recomendações relacionadas ao código alterado ou revisado. Não refatore código fora do escopo apenas para aplicar todas as regras deste documento.
3. Ordem de prioridade: correção e segurança, depois clareza e simplicidade, depois desempenho.
4. Instruções explícitas do usuário e convenções já estabelecidas no repositório prevalecem sobre preferências de estilo. Defeitos de segurança ou correção devem ser ao menos comunicados.
5. Prefira os mecanismos nativos do Spring Boot já presentes no projeto (`@Transactional`, `RestClient`, Bean Validation, Micrometer/OpenTelemetry, `TaskExecutor`) em vez de recriar infraestrutura manualmente com APIs de baixo nível do JDK.
6. Não use recursos em prévia (preview) ou incubação, a menos que o `pom.xml` já habilite `--enable-preview` e a tarefa os exija.
7. Respeite contratos existentes: assinaturas públicas, formatos JSON/Protobuf, migrações Flyway, esquemas de banco e comportamento observável.
8. Preserve a compatibilidade com **GraalVM Native Image / Spring AOT**: evite reflexão dinâmica arbitrária, proxies manuais fora do padrão do Spring e leitura de classpath sem declarar recursos.
9. Não adicione dependências ao `pom.xml` sem necessidade comprovada e autorização.
10. Diferencie defeitos reais (prioridade crítica ou alta) de preferências de estilo (prioridade baixa).
11. Trate desempenho com base em requisitos e evidências (métricas do Actuator/Micrometer, JFR, planos de execução SQL). Não faça otimizações especulativas.
12. Ao encontrar um problema fora do escopo da alteração solicitada, não o corrija silenciosamente; comunique-o com localização, prioridade e sugestão quando relevante.

## 2. Convenções deste guia

| Prioridade | Critério |
| --- | --- |
| Crítica | Vulnerabilidade explorável, vazamento de segredo ou corrupção/perda de dados (especialmente financeiros) |
| Alta | Defeito provável de correção, quebra de transação, condição de corrida, vazamento de recurso ou erro em runtime |
| Média | Problema de design, acoplamento ou manutenção que aumenta o risco de defeitos futuros |
| Baixa | Legibilidade, estilo ou ganho marginal; normalmente uma preferência |

Natureza do **Fundamento** de cada recomendação:

- **Contrato/especificação:** decorre da JLS, do Javadoc da API, de uma JEP ou da especificação do Jakarta EE / Spring.
- **Segurança:** decorre de guias de codificação segura (Oracle, OWASP).
- **Consenso técnico:** prática consolidada, com justificativa técnica, mas dependente de contexto.
- **Preferência:** escolha de estilo; siga a convenção local do projeto.

## 3. Compatibilidade e configuração do projeto

- O `pom.xml` define `<java.version>25</java.version>` sem `--enable-preview`.
- Caso o projeto ou algum módulo tenha a versão reduzida futuramente, confirme `<java.version>` no `pom.xml` antes de empregar recursos introduzidos em versões recentes (cada item da seção 4 informa a versão mínima).
- O projeto inclui suporte a **GraalVM Native Image** (`native-maven-plugin`) e aprimoramento de bytecode do Hibernate (`hibernate-maven-plugin`). Mantenha construtores, anotações de injeção e entidades compatíveis com processamento AOT.

## 4. Recursos modernos do Java aplicados ao Spring

Referência geral: [Java Language Updates (Java 25)](https://docs.oracle.com/en/java/javase/25/language/java-language-changes.html).

### 4.1 Modelagem de tipos e dados

#### Records

Classes transparentes e imutáveis (rasas) para transporte e modelagem de valor.

- **Disponibilidade:** Java 16, [JEP 395](https://openjdk.org/jeps/395). Final.
- **Quando usar no Spring:** DTOs de entrada/saída REST (suporte nativo no Jackson e no Bean Validation), projeções de leitura do Spring Data JPA e jOOQ, eventos de aplicação (`ApplicationEvent`), objetos de valor de domínio e classes `@ConfigurationProperties`.
- **Quando evitar:** entidades JPA (`@Entity`), que exigem construtor sem argumentos, estado mutável gerenciado pelo `EntityManager` e proxies do Hibernate; mensagens gRPC geradas pelo Protobuf (já possuem classes próprias).
- **Compatibilidade:** mínimo Java 16. A imutabilidade é rasa: valide argumentos e faça cópia defensiva (`List.copyOf`) no construtor compacto.

```java
public record CreateTransactionRequest(
        @NotBlank String accountId,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate competenceDate,
        List<String> tags
) {
    public CreateTransactionRequest {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
```

#### Sealed classes e interfaces

Restringem quais tipos podem estender ou implementar uma hierarquia, permitindo verificação de exaustividade pelo compilador.

- **Disponibilidade:** Java 17, [JEP 409](https://openjdk.org/jeps/409). Final.
- **Quando usar:** modelar resultados de regras de negócio na camada de serviço/domínio (por exemplo, sucesso vs. motivos de rejeição conhecidos) em conjunto com `records`, evitando usar exceções para fluxo de negócio esperado.
- **Quando evitar:** beans do Spring (`@Service`, `@Repository`, `@Component`) que precisam ser estendidos por proxies de AOP/transação, ou hierarquias de entidades JPA.
- **Compatibilidade:** mínimo Java 17.

#### Enums

Conjunto fixo de constantes nomeadas.

- **Disponibilidade:** Java 5. Final.
- **Quando usar:** estados fixos, tipos de lançamento, moedas ou periodicidades. Em JPA, use sempre `@Enumerated(EnumType.STRING)` (ou um `AttributeConverter` explícito) em vez de `EnumType.ORDINAL`, para evitar corrupção de dados ao reordenar constantes.
- **Quando evitar:** quando cada variante carrega atributos de estruturas diferentes (prefira `sealed interface` com `records`).

#### Flexible constructor bodies

Permitem validar ou preparar argumentos antes de `super(...)` ou `this(...)`, desde que não referenciem `this`.

- **Disponibilidade:** Java 25, [JEP 513](https://openjdk.org/jeps/513). Final (prévia de 22 a 24).
- **Quando usar:** normalizar ou validar argumentos em exceções de domínio ou classes que estendam tipos base de bibliotecas antes de repassá-los ao `super(...)`.
- **Quando evitar:** não use como incentivo para criar herança entre componentes de negócio (ver [DES-02](#des-02-herança-para-reutilização-de-código)).
- **Compatibilidade:** mínimo Java 25.

### 4.2 Pattern matching e switch

#### Switch expressions, pattern matching e record patterns

`switch` como expressão exaustiva ([JEP 361](https://openjdk.org/jeps/361), Java 14), `instanceof` com variável de padrão ([JEP 394](https://openjdk.org/jeps/394), Java 16) e padrões de tipo/record com guardas `when` em `switch` ([JEP 440](https://openjdk.org/jeps/440) e [JEP 441](https://openjdk.org/jeps/441), Java 21).

- **Quando usar:** traduzir resultados de domínio (`sealed interface` + `records`) em respostas HTTP (`ResponseEntity` / `ProblemDetail`) ou status gRPC sem cadeias de `if/else` nem casts manuais.
- **Quando evitar:** não adicione ramo `default` em `switch` sobre tipo `sealed` ou `enum`, para que o compilador alerte caso um novo subtipo/constante seja criado.
- **Compatibilidade:** mínimo Java 21 para record patterns e `when`.

```java
public sealed interface TransferOutcome permits TransferCompleted, InsufficientFunds, AccountBlocked {}
public record TransferCompleted(UUID transactionId) implements TransferOutcome {}
public record InsufficientFunds(BigDecimal missingAmount) implements TransferOutcome {}
public record AccountBlocked(String reason) implements TransferOutcome {}

ResponseEntity<?> toHttpResponse(TransferOutcome outcome) {
    return switch (outcome) {
        case TransferCompleted(var id) -> ResponseEntity.ok(Map.of("transactionId", id));
        case InsufficientFunds(var missing) -> ResponseEntity.unprocessableEntity()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Saldo insuficiente. Faltam: " + missing));
        case AccountBlocked(var reason) -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, reason));
    };
}
```

#### Unnamed variables and patterns (`_`)

`_` para variáveis de exceção, parâmetros de lambda ou componentes de record intencionalmente não utilizados.

- **Disponibilidade:** Java 22, [JEP 456](https://openjdk.org/jeps/456). Final.
- **Quando usar:** parâmetros ignorados em lambdas, desestruturação parcial de records ou `catch` onde a variável não é inspecionada.
- **Quando evitar:** quando capturar a exceção exigir logar ou encadear a causa (ver [COR-03](#cor-03-exceções-engolidas-perda-de-causa-ou-quebra-de-rollback)).
- **Compatibilidade:** mínimo Java 22.

### 4.3 Strings, datas e valores monetários

#### Text blocks e métodos modernos de String

Text blocks `"""` ([JEP 378](https://openjdk.org/jeps/378), Java 15) e métodos `isBlank()`, `strip()`, `formatted()` ([Javadoc de String](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/String.html)).

- **Quando usar:** consultas estáticas em `@Query` do Spring Data, SQL em testes, payloads JSON em testes de integração e templates de mensagens.
- **Quando evitar:** nunca interpole variáveis externas em SQL/JPQL com `.formatted()` ou `+` (ver [SEC-01](#sec-01-consultas-montadas-por-concatenação)).

#### API `java.time` e `BigDecimal`

- **`java.time` (Java 8, [JEP 150](https://openjdk.org/jeps/150)):** use `Instant` ou `OffsetDateTime` para carimbos de tempo (`TIMESTAMPTZ` no PostgreSQL) e `LocalDate` para datas contábeis/vencimentos (`DATE`).
- **`BigDecimal` ([Javadoc](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html)):** obrigatório para valores monetários, taxas e percentuais financeiros, mapeado para `NUMERIC(precisão, escala)` no PostgreSQL/Flyway. Ver [COR-01](#cor-01-valores-monetários-em-ponto-flutuante).

#### Inferência de tipo com `var`

- **Disponibilidade:** Java 10, [JEP 286](https://openjdk.org/jeps/286). Final.
- **Quando usar:** variáveis locais em que o tipo fica explícito no lado direito (construtores, `Builder`, queries tipadas do jOOQ com tipos longos de `Record*`) ou em testes (arrange/act).
- **Quando evitar:** quando oculta o tipo retornado por um método de negócio ou reduz a clareza da leitura. É uma **preferência**.

### 4.4 Coleções, streams e Optional

#### Fábricas imutáveis e Sequenced Collections

- **Fábricas (`List.of`, `Set.of`, `Map.of`, `List.copyOf`, `Stream.toList()` — Java 9 a 16):** prefira para retornos de métodos e DTOs. Atenção: não aceitam `null` e lançam `UnsupportedOperationException` se modificadas. Em entidades JPA, **não inicialize coleções mapeadas (`@OneToMany`, `@ManyToMany`) com `List.of()`**, pois o Hibernate precisa de coleções mutáveis (`new ArrayList<>()`).
- **Sequenced Collections ([JEP 431](https://openjdk.org/jeps/431), Java 21):** use `list.getFirst()`, `list.getLast()` e `list.reversed()` em vez de aritmética de índices (`list.get(list.size() - 1)`), verificando antes se a coleção não está vazia.

#### Streams e Stream Gatherers

- **Streams (Java 8) e Gatherers ([JEP 485](https://openjdk.org/jeps/485), Java 24):** use para transformações em memória sem efeitos colaterais (`Gatherers.windowFixed`, `fold`, `scan`).
- **Quando evitar no Spring:** não carregue tabelas inteiras em memória para filtrar ou agregar com `Stream` — delegue filtros, somatórios (`SUM`) e paginação (`Pageable`) ao banco de dados via JPA ou jOOQ.

#### Optional

- **Disponibilidade:** Java 8 (`orElseThrow()` no Java 10, `isEmpty()` no Java 11). [Javadoc](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Optional.html).
- **Quando usar:** retorno de métodos de repositório ou serviço que buscam um único item que pode não existir.
- **Quando evitar:** campos de `@Entity` ou DTOs, parâmetros de métodos e retorno de listas (retorne lista vazia). Ver [COR-05](#cor-05-null-ambíguo-e-uso-inadequado-de-optional).

### 4.5 Concorrência e Virtual Threads no Spring

Guia oficial: [Virtual Threads (Java 25)](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html).

- **Disponibilidade:** Java 21 ([JEP 444](https://openjdk.org/jeps/444)). No Java 24+, blocos `synchronized` não fixam (*pin*) mais a virtual thread à thread portadora ([JEP 491](https://openjdk.org/jeps/491)).
- **Como usar no Spring Boot:** não instancie executores manualmente em controllers ou services para atender requisições. Habilite via configuração (`spring.threads.virtual.enabled=true`, quando autorizado) ou injete o `TaskExecutor` gerenciado pelo Spring.
- **Propagação de contexto:** para rastreamento distribuído (Micrometer Tracing / OpenTelemetry), MDC de logs e segurança, **use os mecanismos do próprio Spring/Micrometer**, e não `ThreadLocal` ou `ScopedValue` ([JEP 506](https://openjdk.org/jeps/506)) criados manualmente na lógica de negócio.
- **Limitação de recursos:** virtual threads permitem milhares de chamadas simultâneas, mas o pool de conexões do banco (HikariCP) e APIs externas continuam limitados. Use `Semaphore` ou resiliência do framework para controlar a concorrência sobre recursos escassos.

### 4.6 Recursos em prévia, incubação ou desaconselhados

Não utilize em código deste projeto:

| Recurso | Status no Java 25 | Orientação para este projeto |
| --- | --- | --- |
| Structured Concurrency ([JEP 505](https://openjdk.org/jeps/505)), Stable Values ([JEP 502](https://openjdk.org/jeps/502)), Primitive Types in Patterns ([JEP 507](https://openjdk.org/jeps/507)), PEM Encodings ([JEP 470](https://openjdk.org/jeps/470)) | Prévia (Preview) | Exigem `--enable-preview`. Não usar em código de produção |
| Vector API ([JEP 508](https://openjdk.org/jeps/508)) | Incubação | Exige módulo `jdk.incubator.vector`. Não aplicável ao domínio |
| Compact Source Files / `void main()` ([JEP 512](https://openjdk.org/jeps/512)) e `import module` ([JEP 511](https://openjdk.org/jeps/511)) | Final no Java 25 | Voltados a scripts/ensino. Em código Spring, use classes explícitas e imports por tipo |
| String Templates ([JEP 430](https://openjdk.org/jeps/430) / [JEP 465](https://openjdk.org/jeps/465)) | **Removido** no Java 23 | Não existe no Java 25 (`STR."..."` não compila) |

## 5. Anti-patterns

Formato: **Problema**, **Sinais de identificação**, **Impacto**, **Evite**, **Prefira**, **Exceções**, **Prioridade** e **Fundamento**.

### 5.1 Segurança

#### SEC-01 Consultas montadas por concatenação

**Problema:** valores externos concatenados em SQL, JPQL ou SQL "plain" do jOOQ.

**Sinais de identificação:** `+`, `String.format` ou `.formatted()` em `@Query`, `EntityManager.createQuery`/`createNativeQuery`, `JdbcTemplate`, ou chamadas `DSL.sql(...)`, `DSL.condition(...)` e `DSL.field(String)` do jOOQ com variáveis não confiáveis.

**Impacto:** injeção de SQL, permitindo leitura, adulteração ou exclusão de dados financeiros.

**Evite:**

```java
// jOOQ com interpolação insegura
dsl.selectFrom(ENTRIES)
   .where("description LIKE '%" + filter + "%'")
   .fetch();
```

**Prefira:** parâmetros nomeados (`:param`) no Spring Data JPA e a DSL tipada do jOOQ (que vincula parâmetros automaticamente).

```java
// jOOQ tipado com bind seguro
dsl.selectFrom(ENTRIES)
   .where(ENTRIES.DESCRIPTION.likeIgnoreCase("%" + filter + "%"))
   .fetch();
```

**Exceções:** nomes dinâmicos de colunas para ordenação, desde que mapeados a partir de um `enum` ou lista fechada de campos permitidos.

**Prioridade:** Crítica.

**Fundamento:** Segurança. [OWASP SQL Injection Prevention](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html).

#### SEC-02 Segredos no código, configurações ou logs

**Problema:** senhas, tokens, chaves privadas ou dados sensíveis expostos no código-fonte, no `application.yaml`, em exceções ou nos logs/spans.

**Sinais de identificação:** credenciais literais em código ou em `src/main/resources/application.yaml`; `log.info`/`log.debug` imprimindo DTOs ou cabeçalhos `Authorization`; `toString()` padrão de `record` ou `@Data`/`@ToString` do Lombok em classes que contêm credenciais ou tokens.

**Impacto:** vazamento de credenciais pelo controle de versão ou pelas ferramentas de observabilidade (Prometheus, OpenTelemetry, logs).

**Evite:** `password: "minha-senha-secreta"` fixo em `application.yaml` de produção ou logar o objeto de autenticação inteiro.

**Prefira:** variáveis de ambiente (`${DB_PASSWORD}`) e sobrescrever `toString()` (ou usar `@ToString.Exclude`) para omitir campos sensíveis.

**Exceções:** credenciais efêmeras exclusivamente em testes locais com Testcontainers (`src/test`).

**Prioridade:** Crítica.

**Fundamento:** Segurança. [OWASP Secrets Management](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html).

#### SEC-03 Entrada externa sem validação nas fronteiras

**Problema:** payloads HTTP (WebMVC) ou mensagens gRPC processados sem validação de formato, limites e regras de domínio, ou retorno de detalhes internos de erro ao cliente.

**Sinais de identificação:** parâmetros `@RequestBody` sem `@Valid`; DTOs sem anotações do Jakarta Validation (`@NotNull`, `@Positive`, `@Size`); serviços gRPC que não validam campos obrigatórios do Protobuf; stack traces ou mensagens de `SQLException` expostos na resposta.

**Impacto:** persistência de estados financeiros inconsistentes, negação de serviço por payloads grandes e exposição da estrutura interna do banco.

**Evite:** receber DTOs sem restrições e deixar falhar na camada de banco com `DataIntegrityViolationException` exposta ao cliente.

**Prefira:** validar com `@Valid` + Jakarta Validation na entrada, reforçar invariantes nos objetos de domínio e traduzir falhas em `@RestControllerAdvice` (usando `ProblemDetail`, RFC 9457) ou `Status` adequado no gRPC, registrando detalhes técnicos apenas no log.

**Exceções:** chamadas internas entre métodos já validados dentro do mesmo limite transacional.

**Prioridade:** Alta.

**Fundamento:** Segurança. [OWASP Input Validation](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html).

#### SEC-04 Desserialização insegura e aleatoriedade previsível

**Problema:** uso de `ObjectInputStream`, tipagem polimórfica irrestrita no Jackson (`activateDefaultTyping`) ou uso de `Random` / `Math.random()` para tokens e códigos de segurança.

**Sinais de identificação:** `new ObjectInputStream(...)`; `@JsonTypeInfo` com classe arbitrária vinda do cliente; `new Random()` ou `ThreadLocalRandom` gerando tokens, chaves de idempotência secretas ou códigos de verificação.

**Impacto:** execução remota de código (RCE) na desserialização e predição de tokens.

**Prefira:** DTOs tipados (JSON via Jackson / Protobuf via gRPC) e `SecureRandom` ([Javadoc](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html)) para qualquer valor sensível à segurança.

**Exceções:** `Random` comum apenas para dados sintéticos de teste sem requisito de segurança.

**Prioridade:** Crítica.

**Fundamento:** Segurança. [OWASP Deserialization](https://cheatsheetseries.owasp.org/cheatsheets/Deserialization_Cheat_Sheet.html).

### 5.2 Correção e persistência

#### COR-01 Valores monetários em ponto flutuante

**Problema:** uso de `double` ou `float` para saldos, lançamentos, tarifas ou conversões, ou uso incorreto de `BigDecimal`.

**Sinais de identificação:** `double`/`float` em entidades, DTOs ou cálculos; `new BigDecimal(0.1)` (construtor `double`); `BigDecimal.equals` para comparar valor numérico (`new BigDecimal("10.0").equals(new BigDecimal("10.00"))` é `false`); `divide` sem `RoundingMode`.

**Impacto:** divergência de centavos, falha em conciliações financeiras e `ArithmeticException` em divisões não exatas.

**Evite:**

```java
BigDecimal price = new BigDecimal(19.99);
if (price.equals(BigDecimal.ZERO)) { ... }
BigDecimal installment = total.divide(BigDecimal.valueOf(3)); // ArithmeticException
```

**Prefira:** construir `BigDecimal` via `String` ou `BigDecimal.valueOf`, comparar valores com `compareTo(...) == 0` e exigir `RoundingMode` explícito (por exemplo, `RoundingMode.HALF_EVEN`) definido pela regra de negócio.

```java
BigDecimal price = new BigDecimal("19.99");
if (price.compareTo(BigDecimal.ZERO) == 0) { ... }
BigDecimal installment = total.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_EVEN);
```

**Exceções:** métricas aproximadas de telemetria (Micrometer) em que o próprio coletor opera com `double`.

**Prioridade:** Crítica.

**Fundamento:** Contrato/especificação. [Javadoc de BigDecimal](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html).

#### COR-02 `equals`, `hashCode` e Lombok incorretos em entidades JPA

**Problema:** uso de `@Data` ou `@EqualsAndHashCode` padrão do Lombok em `@Entity`, ou cálculo de `hashCode` baseado em `@Id` gerado pelo banco antes de persistir.

**Sinais de identificação:** `@Data` ou `@EqualsAndHashCode` (sem configuração explícita) sobre uma classe `@Entity`; inclusão de relacionamentos `@OneToMany` / `@ManyToOne` lazy em `equals`, `hashCode` ou `toString`.

**Impacto:** objetos mudam de `hashCode` após o `persist`/`flush` e "somem" de `Set`/`Map`; `LazyInitializationException` fora da transação; `StackOverflowError` em relacionamentos bidirecionais; disparo acidental de queries no banco apenas ao logar ou comparar entidades.

**Evite:**

```java
@Entity
@Data // Gera equals/hashCode/toString com todos os campos e associações
public class AccountEntity { ... }
```

**Prefira:** em entidades JPA, usar apenas `@Getter`/`@Setter` (quando necessário), excluir relacionamentos lazy de `toString` (`@ToString.Exclude`) e implementar `equals`/`hashCode` baseados em chave natural imutável (`@NaturalId`) ou no ID com `hashCode()` constante por classe:

```java
@Override
public final boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AccountEntity other)) return false;
    return id != null && id.equals(other.getId());
}

@Override
public final int hashCode() {
    return getClass().hashCode();
}
```

**Exceções:** em DTOs e objetos de valor sem ciclo de vida JPA, prefira `record` (que já gera `equals`, `hashCode` e `toString` corretos).

**Prioridade:** Alta.

**Fundamento:** Contrato/especificação ([Object.equals](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Object.html) / especificação Jakarta Persistence).

#### COR-03 Exceções engolidas, perda de causa ou quebra de rollback

**Problema:** capturar exceções sem relançar ou sem passar a causa original (`e`), ou capturar `RuntimeException` dentro de métodos `@Transactional` impedindo que o Spring marque a transação para rollback.

**Sinais de identificação:** `catch` vazio; `e.printStackTrace()`; `throw new BusinessException(e.getMessage())` (perde o stack trace original); `log.error` seguido de `throw e` na mesma camada (duplica logs); `try/catch` engolindo exceção de persistência dentro de `@Transactional`.

**Impacto:** transações parcialmente gravadas ou falhas posteriores com `UnexpectedRollbackException`, além de perda da causa raiz nos logs e traces do OpenTelemetry.

**Evite:**

```java
try {
    repository.save(entry);
} catch (Exception e) {
    log.error("Erro: " + e.getMessage());
    throw new DomainException("Falha ao salvar lançamento"); // causa 'e' perdida
}
```

**Prefira:** encadear sempre a causa (`new DomainException("...", e)`), capturar apenas exceções que você sabe tratar naquele ponto e deixar o tratamento/logging de falhas não recuperáveis para a fronteira (`@RestControllerAdvice` / interceptor gRPC).

**Exceções:** operações secundárias não críticas (como envio de notificação opcional) isoladas fora da transação principal, desde que documentadas e registradas em log com nível adequado.

**Prioridade:** Alta.

**Fundamento:** Contrato/especificação ([Throwable](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Throwable.html) e semântica de rollback do `@Transactional` no Spring).

#### COR-04 Recursos externos e Streams de consulta não fechados

**Problema:** abrir arquivos, `InputStream` ou `Stream<T>` retornado por consultas do Spring Data JPA / jOOQ sem `try-with-resources`.

**Sinais de identificação:** `Files.lines(...)`, `Files.walk(...)`, `new FileInputStream(...)` ou métodos de repositório que retornam `Stream<Entity>` consumidos fora de um bloco `try (...)`.

**Impacto:** vazamento de descritores de arquivo ou retenção de cursores/conexões do pool HikariCP até o esgotamento.

**Evite:** `repository.streamAllByStatus(OPEN).forEach(this::process);`

**Prefira:**

```java
try (Stream<EntryEntity> stream = repository.streamAllByStatus(OPEN)) {
    stream.forEach(this::process);
}
```

**Exceções:** `EntityManager`, `JdbcTemplate` e `DSLContext` injetados pelo Spring têm suas conexões gerenciadas automaticamente quando usados em métodos padrão (`findAll`, `fetch`, `query`); não feche o `DataSource` ou o `EntityManager` injetado manualmente.

**Prioridade:** Alta.

**Fundamento:** Contrato/especificação ([JLS 14.20.3 try-with-resources](https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.20.3)).

#### COR-05 `null` ambíguo e uso inadequado de `Optional`

**Problema:** retornar `null` em métodos que devolvem coleções ou buscas, chamar `Optional.get()` sem verificar presença, ou usar `Optional` em campos e parâmetros.

**Sinais de identificação:** `return null;` para listas; `optional.get()`; `if (opt.isPresent()) return opt.get();`; campos `private Optional<String> description;` em entidades ou DTOs.

**Impacto:** `NullPointerException`, `NoSuchElementException`, problemas de serialização no Jackson/JPA e código verboso.

**Prefira:** retornar coleções vazias (`List.of()`); usar `Optional` apenas como retorno de busca unitária e consumi-lo com `.map(...)`, `.orElse(...)` ou `.orElseThrow(() -> new ResourceNotFoundException(id))`.

**Exceções:** campos anuláveis em `@Entity` e `record` de DTO (represente como referência normal, usando `@Nullable` se adotado, nunca como campo `Optional`).

**Prioridade:** Média.

**Fundamento:** Contrato/especificação ([Javadoc de Optional](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Optional.html)).

#### COR-06 Datas legadas, fuso implícito e relógio fixo no código

**Problema:** uso de `java.util.Date`/`Calendar`/`SimpleDateFormat`, uso de `LocalDateTime` para instantes globais ou chamadas diretas a `LocalDate.now()` / `Instant.now()` em regras financeiras.

**Sinais de identificação:** `new Date()`, `SimpleDateFormat`, `LocalDateTime.now()` para auditoria de eventos (`createdAt`), ou cálculos de juros/vencimento chamando `LocalDate.now()` diretamente sem `Clock`.

**Impacto:** ambiguidade de fuso horário entre a JVM e o PostgreSQL, e impossibilidade de testar cenários de virada de mês, fim de semana ou vencimento de forma determinística.

**Prefira:** `Instant` (ou `OffsetDateTime`) para carimbos de tempo, `LocalDate` para datas de negócio e injeção de um bean `java.time.Clock` (`Clock.systemUTC()` em produção, `Clock.fixed(...)` nos testes).

**Exceções:** conversão pontual na fronteira com bibliotecas que exijam `Date`.

**Prioridade:** Média (Alta se `SimpleDateFormat` for compartilhado entre threads).

**Fundamento:** Contrato/especificação ([java.time](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/package-summary.html)).

#### COR-07 Comparação de objetos e wrappers com `==`

**Problema:** comparar `String`, `Long`, `Integer`, `UUID` ou `BigDecimal` usando `==` ou `!=`.

**Sinais de identificação:** `if (account.getId() == targetId)` onde os IDs são `Long` ou `UUID`; `if (status == "PAID")`.

**Impacto:** para `Long` e `Integer`, o cache da JVM cobre apenas `-128` a `127`: testes com IDs `1L` e `2L` passam, mas em produção com ID `1000L` a comparação retorna `false`.

**Prefira:** `Objects.equals(a, b)` ou `.equals(...)` (e `compareTo(...) == 0` para valor numérico de `BigDecimal`).

**Exceções:** constantes `enum` e tipos primitivos (`long`, `int`, `boolean`), nos quais `==` é o correto.

**Prioridade:** Alta.

**Fundamento:** Contrato/especificação ([JLS 15.21.3](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.21.3) e [JLS 5.1.7](https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.7)).

### 5.3 Concorrência e estado em beans Spring

#### CON-01 Estado mutável em beans Singleton e condições de corrida

**Problema:** armazenar estado de uma requisição ou operação em campos de `@Service`, `@RestController`, `@Component` ou `@Repository` (que são singletons compartilhados por todas as threads), ou executar lógica *check-then-act* sobre saldo/estoque sem controle de concorrência no banco.

**Sinais de identificação:** campos não `final` ou coleções mutáveis (`new ArrayList<>()`, `new HashMap<>()`) em classes `@Service`/`@Controller`; leitura de saldo seguida de atualização (`account.setBalance(...)`) sem lock otimista (`@Version`) ou pessimista/atômico no banco.

**Impacto:** vazamento de dados de um usuário para outro em requisições simultâneas e saldo corrompido por *lost updates*.

**Evite:**

```java
@Service
public class BalanceService {
    private BigDecimal lastCalculatedBalance; // Compartilhado entre todas as requisições!
}
```

**Prefira:** manter beans do Spring completamente *stateless* (apenas dependências `private final`), passar dados via parâmetros de método e proteger atualizações concorrentes de saldo no banco com `@Version` (lock otimista), `SELECT ... FOR UPDATE` ou `UPDATE` atômico.

**Exceções:** estruturas em memória explicitamente thread-safe (`ConcurrentHashMap` com `computeIfAbsent`, `AtomicLong`) quando um cache local simples for justificável.

**Prioridade:** Crítica em operações de saldo; Alta nos demais casos.

**Fundamento:** Contrato/especificação ([JLS 17.4 Memory Model](https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.4) e escopo singleton padrão do Spring).

#### CON-02 Criação manual de threads, interrupções descartadas e mau uso de Virtual Threads

**Problema:** criar `new Thread(...)` ou pools fixos desconectados do Spring, engolir `InterruptedException`, usar `.parallelStream()` em operações de I/O/transação ou fazer *pooling* de virtual threads.

**Sinais de identificação:** `new Thread(...).start()`; `Executors.newFixedThreadPool(...)` sem gerenciamento de ciclo de vida; `catch (InterruptedException e) {}`; `.parallelStream()` chamando banco ou API externa (perde o contexto transacional `@Transactional` e de segurança/tracing da thread original).

**Impacto:** transações órfãs, perda de spans do OpenTelemetry, impossibilidade de desligamento gracioso da aplicação e contenção no `ForkJoinPool.commonPool()`.

**Prefira:** usar execuções gerenciadas pelo Spring, manter streams sequenciais por padrão e, ao capturar `InterruptedException`, sempre restaurar a flag de interrupção:

```java
catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Operação interrompida", e);
}
```

**Exceções:** algoritmos puramente em memória e intensivos em CPU, sem I/O nem acesso a contexto do Spring, com ganho comprovado por medição.

**Prioridade:** Alta.

**Fundamento:** Contrato/especificação ([Virtual Threads (Java 25)](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html) e [java.util.stream Side-effects](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/package-summary.html#SideEffects)).

### 5.4 Design e manutenibilidade

#### DES-01 Responsabilidades excessivas e acoplamento de camadas

**Problema:** controllers ou serviços gRPC contendo regras de negócio e acesso direto ao banco, ou entidades JPA expostas diretamente na API REST/gRPC.

**Sinais de identificação:** `@RestController` injetando `JpaRepository` ou `DSLContext` com cálculos financeiros no meio do endpoint; retorno de `@Entity` diretamente no controller (acopla o contrato JSON à tabela do banco e causa `LazyInitializationException` ou vulnerabilidade de *Mass Assignment*).

**Impacto:** quebra de contrato de API ao evoluir o banco de dados, dificuldade de reutilizar a mesma regra entre REST e gRPC, e testes lentos.

**Prefira:** separar transporte (Controller REST / Serviço gRPC com DTOs), aplicação/domínio (`@Service` com regras de negócio e `@Transactional`) e persistência (Spring Data JPA / jOOQ). Refatore **apenas quando a tarefa já envolver a criação ou alteração desse fluxo**.

**Exceções:** endpoints puramente de leitura simples (consulta direta projetada em `record` DTO).

**Prioridade:** Média.

**Fundamento:** Consenso técnico.

#### DES-02 Herança para reutilização de código

**Problema:** criar classes base genéricas (`BaseService`, `AbstractController`, `BaseEntity` inchada) para compartilhar métodos utilitários.

**Sinais de identificação:** hierarquias `extends` entre serviços ou controllers; campos `protected` mutáveis compartilhados.

**Impacto:** alto acoplamento (classe base frágil) e dificuldade para testar componentes isoladamente.

**Prefira:** composição via injeção por construtor de colaboradores especializados.

**Exceções:** uma superclasse `@MappedSuperclass` enxuta apenas para campos de auditoria padronizados (por exemplo, `createdAt`, `updatedAt`) quando essa for a convenção do projeto, ou classes base exigidas pelo Spring/gRPC (`*ImplBase` gerado pelo Protobuf).

**Prioridade:** Média.

**Fundamento:** Consenso técnico.

#### DES-03 Abstrações especulativas (`*Service` + `*ServiceImpl`)

**Problema:** criar interfaces `FooService` com uma única implementação `FooServiceImpl`, fábricas sem necessidade ou genéricos complexos para um único caso de uso.

**Sinais de identificação:** par `XService` / `XServiceImpl` onde nunca haverá segunda implementação; camadas que apenas repassam chamadas sem agregar regra nem traduzir fronteira.

**Impacto:** duplicação de assinaturas, navegação indireta desnecessária e mais arquivos para manter. No Spring moderno, proxies funcionam diretamente em classes concretas.

**Prefira:** classe concreta `@Service public class TransactionService` até surgir uma necessidade real de múltiplas implementações (por exemplo, integrações com provedores externos distintos).

**Exceções:** interfaces de repositório do Spring Data (`interface TransactionRepository extends JpaRepository<...>`, pois o framework gera a implementação) e portas para clientes externos quando houver múltiplas implementações. Não remova interfaces já existentes no projeto sem solicitação.

**Prioridade:** Baixa.

**Fundamento:** Preferência com justificativa de simplicidade.

#### DES-04 Mutabilidade desnecessária e exposição de estado interno

**Problema:** expor coleções mutáveis internas ou permitir alteração irrestrita de estado sem preservar invariantes de negócio.

**Sinais de identificação:** `@Data` ou `@Setter` em todas as classes indiscriminadamente; DTOs mutáveis com construtor vazio e setters; entidades com `setBalance(...)` público em vez de métodos de domínio que validam a operação (`credit(...)`, `debit(...)`).

**Impacto:** estados inválidos criados fora da regra de negócio e coleções alteradas acidentalmente por chamadores.

**Prefira:** `record` para DTOs e objetos de valor; campos `private final` em serviços; em entidades JPA, construtor `protected` sem argumentos (para o Hibernate) e métodos de negócio explícitos para alterar o estado.

**Exceções:** classes de configuração ou integrações legadas que exijam JavaBeans padrão com getters/setters.

**Prioridade:** Média.

**Fundamento:** Consenso técnico ([Secure Coding Guidelines for Java SE](https://www.oracle.com/java/technologies/javase/seccodeguide.html)).

### 5.5 Desempenho e acesso a dados

#### PER-01 Consultas N+1, I/O em laços e transações longas

**Problema:** executar consultas ao banco ou chamadas HTTP/gRPC dentro de um laço (`for` / `stream().map()`), acessar coleções lazy do Hibernate em loop, ou manter `@Transactional` aberto durante chamadas de rede externas.

**Sinais de identificação:** `repository.findById(...)` dentro de laço; relacionamento `@ManyToOne` com `FetchType.EAGER` padrão (no JPA, `@ManyToOne` e `@OneToOne` são `EAGER` por padrão!); método `@Transactional` que chama `RestClient` ou stub gRPC antes de concluir.

**Impacto:** dezenas ou centenas de queries para uma única requisição, esgotamento rápido do pool de conexões HikariCP (conexão fica presa no banco enquanto aguarda chamada HTTP externa) e timeouts em cascata.

**Evite:**

```java
@Transactional
public void processAll(List<UUID> ids) {
    for (UUID id : ids) {
        var entry = repository.findById(id).orElseThrow(); // N queries
        externalClient.notify(entry); // Rede externa dentro da transação de banco!
    }
}
```

**Prefira:**
- Configurar `@ManyToOne(fetch = FetchType.LAZY)` e `@OneToOne(fetch = FetchType.LAZY)` explicitamente.
- Buscar dados em lote (`findAllById`, `JOIN FETCH`, `@EntityGraph`, ou projeção direta com jOOQ).
- Usar `@Transactional(readOnly = true)` em consultas de leitura (evita *dirty checking* desnecessário no Hibernate).
- Executar chamadas HTTP/gRPC externas **fora** do escopo `@Transactional` de banco de dados.

**Exceções:** lotes comprovadamente minúsculos e limitados (ex.: 2 ou 3 itens fixos), mas ainda assim sem chamadas de rede lentas dentro da transação.

**Prioridade:** Alta.

**Fundamento:** Consenso técnico e especificação Jakarta Persistence.

#### PER-02 Otimização prematura sem medição

**Problema:** introduzir complexidade (caches manuais, estruturas customizadas, consultas nativas obscuras, paralelismo manual) sem evidência de gargalo.

**Sinais de identificação:** cache em memória sem expiração nem invalidação; substituição de código legível por micro-otimizações manuais sem métricas do Micrometer/Prometheus ou profiling JFR.

**Impacto:** bugs de dados desatualizados (*stale cache*), vazamento de memória e código difícil de manter.

**Prefira:** escrever código simples e consultas SQL/JPQL/jOOQ bem indexadas primeiro; medir com Actuator/Prometheus/OpenTelemetry ou [JMH](https://github.com/openjdk/jmh) antes de otimizar.

**Exceções:** requisitos não funcionais de latência/vazão explicitamente definidos na tarefa e validados por teste/medição.

**Prioridade:** Baixa.

**Fundamento:** Consenso técnico.

### 5.6 Testes

#### TST-01 Dependências ocultas e injeção por campo

**Problema:** usar `@Autowired` em campos privados no código de produção ou instanciar serviços/clientes de I/O com `new` dentro de métodos de negócio.

**Sinais de identificação:** `@Autowired private FooRepository repository;` em `@Service` ou `@Controller`; chamadas estáticas a serviços externos.

**Impacto:** impossibilita instanciar a classe diretamente em testes unitários rápidos sem subir o contexto do Spring ou usar reflexão, além de ocultar o excesso de dependências da classe.

**Evite:**

```java
@Service
public class ReportService {
    @Autowired
    private EntryRepository repository;
}
```

**Prefira:** injeção por construtor com campos `private final` (no Spring, o `@Autowired` é dispensável quando há um único construtor; ou `@RequiredArgsConstructor` se o projeto já padronizar esse uso do Lombok).

```java
@Service
public class ReportService {
    private final EntryRepository repository;

    public ReportService(EntryRepository repository) {
        this.repository = repository;
    }
}
```

**Exceções:** em classes de teste (`*Tests.java`), `@Autowired` em campo é o padrão idiomático do Spring Test e é perfeitamente aceitável.

**Prioridade:** Média.

**Fundamento:** Consenso técnico e recomendação oficial do Spring Framework.

#### TST-02 Testes frágeis, lentos ou acoplados à implementação

**Problema:** subir `@SpringBootTest` completo para testar regras puras de domínio, usar `Thread.sleep` para sincronização, depender da data/hora da máquina ou usar mocks para testar queries SQL/JPA.

**Sinais de identificação:** `@SpringBootTest` em todos os testes unitários; `Thread.sleep(...)`; mocks verificando cada método privado/auxiliar interno; mockar `DSLContext` do jOOQ ou `JpaRepository` quando o objetivo do teste é validar se a query SQL/JPQL funciona no PostgreSQL.

**Impacto:** suíte lenta, falsos positivos (o mock passa, mas o SQL falha no PostgreSQL real) e testes que quebram a cada pequena refatoração interna.

**Prefira:**
- **Testes unitários rápidos (JUnit 5 puro + AssertJ/Mockito):** para regras de domínio, cálculos financeiros e serviços, instanciando a classe pelo construtor com `Clock.fixed(...)`.
- **Testes de fatia (*slice tests*) ou integração com Testcontainers:** usar a configuração existente ([TestcontainersConfiguration.java](file:///c:/projects/university/gestao-financeira/src/test/java/com/emanudev/gestaofinanceira/TestcontainersConfiguration.java) com `@Import(TestcontainersConfiguration.class)`), `@DataJpaTest` / `@JooqTest` / `@WebMvcTest` para validar queries reais no PostgreSQL, migrações Flyway e contratos HTTP/gRPC.
- Isolar os dados de cada teste para que a ordem de execução nunca afete o resultado.

**Exceções:** um teste de fumaça (`@SpringBootTest`) que valida a subida do contexto completo da aplicação (como [GestaoFinanceiraApplicationTests.java](file:///c:/projects/university/gestao-financeira/src/test/java/com/emanudev/gestaofinanceira/GestaoFinanceiraApplicationTests.java)).

**Prioridade:** Média.

**Fundamento:** Consenso técnico.

## 6. Referências

- [Documentação do Java SE 25](https://docs.oracle.com/en/java/javase/25/) e [Java SE 25 API Javadoc](https://docs.oracle.com/en/java/javase/25/docs/api/index.html)
- [Java Language Specification, Java SE 25](https://docs.oracle.com/javase/specs/jls/se25/html/index.html)
- [JDK 25: lista de JEPs](https://openjdk.org/projects/jdk/25/) e [Java Language Updates (Java 25)](https://docs.oracle.com/en/java/javase/25/language/java-language-changes.html)
- [Virtual Threads (Java 25)](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html)
- [Secure Coding Guidelines for Java SE](https://www.oracle.com/java/technologies/javase/seccodeguide.html)
- [OWASP Cheat Sheet Series](https://cheatsheetseries.owasp.org/)
