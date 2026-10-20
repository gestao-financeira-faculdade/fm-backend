# Guia de Arquitetura Hexagonal (Ports and Adapters) — Java 25 / Spring Boot

Referência operacional para agentes de IA ao criar, modificar, refatorar ou revisar código Java com separação arquitetural entre domínio, aplicação e adaptadores neste repositório. Complementa o guia [JAVA_BEST_PRACTICES.md](./JAVA_BEST_PRACTICES.md).

- **Contexto do repositório:** `com.emanudev.gestaofinanceira` (Java 25 sem `--enable-preview`, Spring Boot 4, WebMVC, gRPC, Spring Data JPA/Hibernate, jOOQ, PostgreSQL/Flyway, Testcontainers e GraalVM Native Image). O projeto está em estágio inicial (apenas classes de bootstrap) e não impõe arquitetura hexagonal a código legado ou módulos que sigam outro padrão.

## Índice

1. [Regras de decisão e desambiguação](#1-regras-de-decisão-e-desambiguação)
2. [Responsabilidades e convenções por camada](#2-responsabilidades-e-convenções-por-camada)
3. [Regras e matriz de dependência](#3-regras-e-matriz-de-dependência)
4. [Organização de pacotes](#4-organização-de-pacotes)
5. [Regras de implementação em Java 25 e Spring](#5-regras-de-implementação-em-java-25-e-spring)
   - [5.1 Portas, casos de uso e registro de beans](#51-portas-casos-de-uso-e-registro-de-beans)
   - [5.2 Domínio rico, objetos de valor e invariantes](#52-domínio-rico-objetos-de-valor-e-invariantes)
   - [5.3 Comandos, consultas, resultados e exceções](#53-comandos-consultas-resultados-e-exceções)
   - [5.4 Fronteiras transacionais](#54-fronteiras-transacionais)
   - [5.5 Mapeamento de modelos e visibilidade](#55-mapeamento-de-modelos-e-visibilidade)
6. [Anti-patterns arquiteturais](#6-anti-patterns-arquiteturais)
   - [HEX-01 Domínio dependente de frameworks ou infraestrutura](#hex-01-domínio-dependente-de-frameworks-ou-infraestrutura)
   - [HEX-02 Casos de uso implementados diretamente em controladores](#hex-02-casos-de-uso-implementados-diretamente-em-controladores)
   - [HEX-03 Portas sem necessidade real ou interfaces 1:1 indiscriminadas](#hex-03-portas-sem-necessidade-real-ou-interfaces-11-indiscriminadas)
   - [HEX-04 Serviços genéricos que acumulam múltiplas responsabilidades](#hex-04-serviços-genéricos-que-acumulam-múltiplas-responsabilidades)
   - [HEX-05 Entidades anêmicas em domínios ricos em regras](#hex-05-entidades-anêmicas-em-domínios-ricos-em-regras)
   - [HEX-06 Regras de negócio espalhadas por controladores e adaptadores](#hex-06-regras-de-negócio-espalhadas-por-controladores-e-adaptadores)
   - [HEX-07 Portas de saída que expõem tipos de ORM ou banco](#hex-07-portas-de-saída-que-expõem-tipos-de-orm-ou-banco)
   - [HEX-08 Entidades de persistência reutilizadas como domínio e contrato HTTP](#hex-08-entidades-de-persistência-reutilizadas-como-domínio-e-contrato-http)
   - [HEX-09 DTOs e mapeamentos redundantes sem benefício claro](#hex-09-dtos-e-mapeamentos-redundantes-sem-benefício-claro)
   - [HEX-10 Acoplamento entre casos de uso e tecnologias externas](#hex-10-acoplamento-entre-casos-de-uso-e-tecnologias-externas)
   - [HEX-11 Dependências circulares entre módulos ou pacotes](#hex-11-dependências-circulares-entre-módulos-ou-pacotes)
   - [HEX-12 Interfaces excessivamente genéricas ou abstrações especulativas](#hex-12-interfaces-excessivamente-genéricas-ou-abstrações-especulativas)
   - [HEX-13 Modelo de domínio complexo para operações CRUD simples](#hex-13-modelo-de-domínio-complexo-para-operações-crud-simples)
   - [HEX-14 Transações abertas além do limite necessário](#hex-14-transações-abertas-além-do-limite-necessário)
   - [HEX-15 Testes de unidade que exigem infraestrutura real](#hex-15-testes-de-unidade-que-exigem-infraestrutura-real)
   - [HEX-16 Testes baseados exclusivamente em mocks nos adaptadores](#hex-16-testes-baseados-exclusivamente-em-mocks-nos-adaptadores)
   - [HEX-17 Eventos, mediadores ou camadas adicionais sem necessidade concreta](#hex-17-eventos-mediadores-ou-camadas-adicionais-sem-necessidade-concreta)
7. [Estratégia de testes](#7-estratégia-de-testes)
8. [Referências](#8-referências)

---

## 1. Regras de decisão e desambiguação

1. **Preserve a arquitetura existente no escopo da tarefa:** se um pacote já utiliza organização tradicional em camadas (`Controller` -> `Service` -> `Repository`), mantenha esse padrão ao modificá-lo. Só aplique a estrutura hexagonal completa quando o pacote já a utilizar ou quando a tarefa solicitar sua criação.
2. **Proibição de refatoração fora do escopo:** não mova classes, não renomeie pacotes e não introduza portas em código adjacente não relacionado à tarefa. Comunique violações encontradas fora do escopo informando localização e prioridade (`Crítica`, `Alta`, `Média` ou `Baixa`, conforme [JAVA_BEST_PRACTICES.md](./JAVA_BEST_PRACTICES.md#2-convenções-deste-guia)).
3. **Subdivisão interna adotada:** quando a arquitetura hexagonal for utilizada neste projeto, divida o código em três áreas com dependências apontando para o centro:
   - `domain`: entidades, agregados, objetos de valor, serviços de domínio e exceções de negócio (100% Java puro, sem Spring/JPA/Jackson/gRPC).
   - `application`: casos de uso (orquestração e `@Transactional`), comandos, consultas, resultados e interfaces das portas de saída.
   - `adapter`: adaptadores de entrada (`in.web`, `in.grpc`) e de saída (`out.persistence`, `out.client`).
4. **Desambiguação — Portas de entrada (*Inbound Ports*):**
   - **Padrão:** exponha a **própria classe concreta do caso de uso** (`@Service public class TransferMoneyUseCase`) como porta de entrada. Não crie um par interface + classe (`TransferMoneyPort` + `TransferMoneyServiceImpl`) com relação 1:1 (alinha-se a [DES-03](./JAVA_BEST_PRACTICES.md#des-03-abstrações-especulativas-service--serviceimpl)).
   - **Exceção:** crie interface para porta de entrada apenas se houver múltiplas implementações, decoradores explícitos ou separação em módulos Maven distintos.
5. **Desambiguação — Portas de saída (*Outbound Ports*):**
   - **Padrão:** crie sempre uma interface na camada `application` (ou `domain`, se acionada por serviço de domínio) para qualquer fronteira de I/O (banco de dados, clientes HTTP/gRPC, mensageria).
   - **Granularidade:** prefira **uma porta coesa por agregado ou integração externa** (ex.: `AccountRepositoryPort` contendo apenas os métodos necessários aos casos de uso, como `findById` e `save`). Separe escrita e leitura em portas distintas apenas quando tiverem implementações tecnológicas diferentes (ex.: `AccountRepositoryPort` implementado com JPA para comandos e `AccountQueryPort` implementado com jOOQ para relatórios).
6. **Desambiguação — Sem cerimônia de *Presenters*:** ao contrário da *Clean Architecture* estrita, não crie *Output Boundaries* com *Presenters* mutáveis. O caso de uso deve retornar diretamente um `record` ou uma `sealed interface` de resultado, cabendo ao adaptador de entrada converter esse retorno para HTTP (`ResponseEntity` / `ProblemDetail`) ou gRPC.
7. **Desambiguação — CRUD e consultas de leitura (*CQRS leve*):**
   - Operações meramente cadastrais sem regras de negócio nem invariantes não justificam separar modelo de domínio de entidade de persistência (ver [HEX-13](#hex-13-modelo-de-domínio-complexo-para-operações-crud-simples)).
   - Consultas somente leitura não devem carregar agregados de domínio: projete os dados diretamente em `records` imutáveis usando jOOQ ou projeções do Spring Data JPA.

| Situação na tarefa | Decisão direta |
| --- | --- |
| Regra de cálculo financeiro, validação de estado ou invariante | Implementar na entidade ou objeto de valor (`domain`), sem dependências externas |
| Orquestração (carregar estado, chamar domínio, salvar, transacionar) | Implementar em classe concreta de caso de uso (`application`) |
| Acesso a PostgreSQL (JPA/jOOQ) ou chamada externa (REST/gRPC) | Definir interface de porta de saída em `application` e implementar em `adapter.out` |
| Endpoint REST ou método de serviço gRPC | Implementar em `adapter.in`, converter payload externo para `Command`/`Query` e chamar o caso de uso |
| Consulta de listagem/relatório sem mutação de estado | Retornar `record` de projeção via porta de consulta (ex.: jOOQ) sem passar por entidade de domínio |

---

## 2. Responsabilidades e convenções por camada

| Componente | O que deve conter | O que NUNCA deve conter |
| --- | --- | --- |
| **Domínio** (`domain`) | - **Entidades/Agregados:** classes com identidade e métodos de negócio que protegem invariantes (`Account.debit(...)`).<br>- **Objetos de Valor:** `records` imutáveis validados no construtor compacto (`Money`).<br>- **Serviços de Domínio:** lógica *stateless* que envolve mais de um agregado.<br>- **Exceções e Eventos de Domínio:** fatos e falhas expressos na linguagem do negócio. | - Anotações ou imports de Spring, JPA/Hibernate (`@Entity`), Jackson (`@JsonProperty`), Bean Validation de transporte, jOOQ ou Protobuf.<br>- Chamadas de I/O, queries ou acesso direto à rede.<br>- `Setters` públicos que permitam estado inválido. |
| **Aplicação e Portas** (`application`) | - **Casos de Uso:** classes `@Service` que coordenam o fluxo de trabalho e demarcam `@Transactional`.<br>- **Entradas e Saídas:** `records` para `*Command`/`*Query` e `sealed interface`/`records` para resultados (`*Outcome` / `*Result`).<br>- **Portas de Saída:** interfaces de repositório, consulta ou integração externa com tipos do domínio/aplicação. | - Cálculos financeiros ou regras de transição de estado que pertençam à entidade de domínio.<br>- Referências a `ResponseEntity`, `HttpStatus`, `HttpServletRequest`, `Pageable` do Spring Data, `DSLContext` do jOOQ, `@Entity` JPA ou stubs gRPC. |
| **Adaptadores de Entrada** (`adapter.in`) | - `@RestController`, `@RestControllerAdvice`, DTOs de request/response HTTP (`records` com `@Valid`).<br>- Serviços gRPC (`extends *ImplBase`).<br>- Conversão de DTO/Protobuf -> `Command`/`Query` e de `Outcome`/exceções de domínio -> HTTP `ProblemDetail` ou `io.grpc.Status`. | - Regras de negócio, cálculos de saldo ou validações de domínio.<br>- Demarcação `@Transactional`.<br>- Injeção direta de adaptadores de saída ou repositórios JPA/jOOQ (exceto se o módulo for CRUD simples não hexagonal). |
| **Adaptadores de Saída** (`adapter.out`) | - Implementações das portas de saída (`*PersistenceAdapter`, `*ClientAdapter`).<br>- Entidades JPA (`@Entity`), interfaces `JpaRepository`, queries tipadas com `DSLContext` (jOOQ), chamadas `RestClient` ou clientes gRPC.<br>- Mapeamento entre modelo de domínio e modelo de persistência/integração, e tradução de exceções técnicas. | - Decisões de negócio ocultas durante o salvamento ou consulta.<br>- Exposição de tipos internos do adaptador (`*JpaEntity`, `Record` do jOOQ) fora do pacote do adaptador. |

---

## 3. Regras e matriz de dependência

Todas as dependências de código-fonte (`import` e referências de tipos) devem apontar **em direção ao domínio**:

| Origem \ Destino permitido | `domain` | `application` | `adapter.in` | `adapter.out` | Frameworks (Web, JPA, jOOQ, gRPC) |
| --- | --- | --- | --- | --- | --- |
| **`domain`** | Sim | **Não** | **Não** | **Não** | **Não** |
| **`application`** | Sim | Sim | **Não** | **Não** | Apenas `@Service` e `@Transactional` |
| **`adapter.in`** | Sim (tipos expostos pela aplicação) | Sim | Sim (mesmo contexto) | **Não** | Sim (Spring Web, Validation, Jackson, gRPC) |
| **`adapter.out`** | Sim | Sim (Portas de Saída) | **Não** | Sim (mesmo contexto) | Sim (Spring Data JPA, Hibernate, jOOQ, RestClient, gRPC) |
| **Configuração (`config`)** | Sim | Sim | Sim | Sim | Sim (`@Configuration`, `@Bean`) |

**Exemplo de fronteira correta (Domínio + Porta + Caso de Uso):**

```java
// 1. Domínio puro (sem dependências externas)
package com.emanudev.gestaofinanceira.account.domain;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import java.util.Objects;
import java.util.UUID;

public final class Account {
    private final UUID id;
    private Money balance;

    public Account(UUID id, Money balance) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.balance = Objects.requireNonNull(balance, "balance must not be null");
    }

    public void debit(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (this.balance.isLessThan(amount)) {
            throw new InsufficientBalanceException(this.id, amount, this.balance);
        }
        this.balance = this.balance.subtract(amount);
    }

    public UUID id() { return id; }
    public Money balance() { return balance; }
}
```

```java
// 2. Porta de saída na camada de aplicação (usa apenas tipos do domínio/Java)
package com.emanudev.gestaofinanceira.account.application;

import com.emanudev.gestaofinanceira.account.domain.Account;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepositoryPort {
    Optional<Account> findById(UUID id);
    void save(Account account);
}
```

---

## 4. Organização de pacotes

Prefira a **organização por contexto/funcionalidade** (*package-by-feature*) no primeiro nível, mantendo as divisões hexagonais dentro de cada contexto. A árvore abaixo é uma **referência ilustrativa** para novos contextos que adotem arquitetura hexagonal:

```text
com.emanudev.gestaofinanceira
├── GestaoFinanceiraApplication.java
├── shared/
│   └── domain/
│       └── Money.java                       # Objeto de valor ubíquo
└── account/                                 # Contexto de negócio
    ├── domain/                              # Java puro: entidades, VOs, exceções de domínio
    │   ├── Account.java
    │   ├── AccountStatus.java
    │   └── InsufficientBalanceException.java
    ├── application/                         # Casos de uso, comandos, resultados e portas de saída
    │   ├── TransferMoneyUseCase.java
    │   ├── TransferMoneyCommand.java
    │   ├── TransferOutcome.java
    │   └── AccountRepositoryPort.java
    └── adapter/
        ├── in/
        │   ├── web/                         # Controllers REST e Request/Response DTOs
        │   │   ├── AccountTransferController.java
        │   │   └── TransferRequest.java
        │   └── grpc/                        # Serviços gRPC
        └── out/
            └── persistence/                 # Implementação das portas (classes package-private)
                ├── AccountPersistenceAdapter.java
                ├── AccountJpaEntity.java
                └── SpringDataAccountRepository.java
```

**Regras para pacotes:**

1. **Evite aninhamento prematuro:** só subdivida `domain` (ex.: `domain.model`, `domain.service`) ou `application` (ex.: `application.port.out`, `application.usecase`) quando o pacote acumular classes suficientes para justificar a separação.
2. **Isolamento entre contextos:** um contexto (`billing`) nunca deve importar classes de `account.adapter.*`. A comunicação entre contextos deve ocorrer via casos de uso públicos (`account.application`) ou objetos de valor em `shared.domain`.
3. **Sem pacotes de despejo:** não crie pacotes `util`, `common` ou `helper` genéricos; coloque classes auxiliares no pacote que as consome com visibilidade *package-private*.

---

## 5. Regras de implementação em Java 25 e Spring

Para detalhes sobre sintaxe e disponibilidade dos recursos do Java 25 (`record`, `sealed`, pattern matching, `_`, `BigDecimal`, `java.time` e Virtual Threads), consulte a [seção 4 de JAVA_BEST_PRACTICES.md](./JAVA_BEST_PRACTICES.md#4-recursos-modernos-do-java-aplicados-ao-spring). Abaixo estão as regras específicas de aplicação arquitetural:

### 5.1 Portas, casos de uso e registro de beans

- **Registro pragmático na camada de aplicação (padrão):** anote a classe concreta do caso de uso com `@Service` e `@Transactional`, injetando portas de saída via construtor (`private final`). Mantenha o pacote `domain` sem nenhuma anotação do Spring.
- **Composição via `@Configuration` (alternativa):** use métodos `@Bean` em uma classe `@Configuration` apenas se o módulo exigir que também a camada `application` não tenha imports do Spring, ou ao compor múltiplas implementações da mesma porta.
- **Nomenclatura sem `Impl`:** nomeie adaptadores pela tecnologia que encapsulam (`AccountPersistenceAdapter`, `JooqAccountQueryAdapter`, `GrpcFraudCheckAdapter`) e casos de uso pelo verbo de negócio (`TransferMoneyUseCase`, `OpenAccountUseCase`).
- **Compatibilidade com GraalVM Native Image:** não utilize reflexão dinâmica nem `java.lang.reflect.Proxy` para instanciar ou conectar portas e adaptadores, preservando a análise estática AOT do `native-maven-plugin`.

### 5.2 Domínio rico, objetos de valor e invariantes

- **Imutabilidade e validação na entrada:** implemente objetos de valor (`Money`, `Percentage`, `DateRange`) como `records` validando invariantes no construtor compacto.
- **Corpos de construtores flexíveis ([JEP 513](https://openjdk.org/jeps/513), Java 25):** ao estender classes de exceção de domínio ou classes base, valide ou normalize os argumentos antes de chamar `super(...)`.
- **Tempo determinístico:** nunca chame `Instant.now()` ou `LocalDate.now()` diretamente dentro de entidades de domínio; receba o instante como parâmetro do método de negócio (`account.close(Instant closedAt)`) ou injete `java.time.Clock` no caso de uso.

### 5.3 Comandos, consultas, resultados e exceções

- **Comandos e Consultas (`*Command` / `*Query`):** `records` imutáveis em `application`, sem anotações HTTP/Jackson/Protobuf. Valide pré-condições estruturais simples (não nulidade, cópia defensiva `List.copyOf`) no construtor compacto.
- **Resultados de negócio esperados vs. exceções:**
  - Para fluxos com múltiplos desfechos de negócio previstos (ex.: transferência aprovada, saldo insuficiente, conta bloqueada), prefira retornar uma `sealed interface` com `records` e traduzi-la no adaptador de entrada com `switch` exaustivo (sem `default`).
  - Para violações de invariantes ou erros que interrompem o fluxo e exigem rollback transacional, lance exceções de domínio específicas (estendendo `RuntimeException`), sem acoplá-las a códigos HTTP ou `io.grpc.Status`.

### 5.4 Fronteiras transacionais

- **Demarcação no caso de uso:** `@Transactional` pertence ao método público do caso de uso em `application` (ou `@Transactional(readOnly = true)` para consultas), nunca ao controller nem ao domínio.
- **Proibição de I/O de rede dentro da transação:** se um caso de uso precisar consultar uma API externa (HTTP/gRPC) e também gravar no PostgreSQL, coordene a chamada externa **fora** do limite transacional de banco de dados (usando `TransactionTemplate` apenas ao redor da persistência ou um componente transacional separado) para não reter conexões do HikariCP.

### 5.5 Mapeamento de modelos e visibilidade

- **Separação entre Entidade de Domínio e `@Entity` JPA:** em módulos hexagonais, a entidade de domínio (`Account`) não leva anotações JPA. O adaptador de saída define `AccountJpaEntity` (com construtor `protected` sem argumentos exigido pelo Hibernate) e converte entre `Account` e `AccountJpaEntity` usando métodos de mapeamento explícitos em Java puro.
- **Quando evitar DTO duplicado na leitura:** em consultas de leitura projetadas como `record` (ex.: `AccountSummaryView`), se o contrato JSON de resposta for idêntico à projeção, o controller pode retornar o `record` diretamente sem criar um `AccountSummaryResponseDto` duplicado.
- **Visibilidade *package-private* nos adaptadores:** declare `AccountJpaEntity`, `SpringDataAccountRepository`, classes de mapeamento interno e a própria classe `AccountPersistenceAdapter` sem o modificador `public` (visibilidade de pacote), deixando apenas a interface da porta pública. Assim, o compilador impede qualquer acoplamento acidental do caso de uso à implementação de persistência.

---

## 6. Anti-patterns arquiteturais

### HEX-01 Domínio dependente de frameworks ou infraestrutura

1. **Problema:** classes de `domain` importam tipos de ORM (`jakarta.persistence.*`), serialização (`com.fasterxml.jackson.*`), transporte (`org.springframework.http.*`, `io.grpc.*`) ou infraestrutura.
2. **Como identificar:** instruções `import org.springframework.*`, `import jakarta.persistence.*` ou `import com.fasterxml.jackson.*` dentro de pacotes `..domain..`.
3. **Consequências:** acopla o ciclo de vida e a evolução do modelo de negócio ao banco de dados ou ao formato de transporte; inviabiliza testes de domínio sem dependências de framework.
4. **Exemplo problemático:**
   ```java
   package com.emanudev.gestaofinanceira.account.domain;
   import jakarta.persistence.Entity;
   import com.fasterxml.jackson.annotation.JsonProperty;

   @Entity
   public class Account {
       @JsonProperty("saldo_atual")
       private BigDecimal balance;
   }
   ```
5. **Alternativa recomendada:** manter `Account` em `domain` apenas com tipos do JDK (`BigDecimal`, `UUID`, `Instant`) e mover `@Entity` para `AccountJpaEntity` (`adapter.out.persistence`) e `@JsonProperty` para o DTO do adaptador web (`adapter.in.web`).
6. **Exceções:** módulos cadastrais simples que não adotam arquitetura hexagonal (ver [HEX-13](#hex-13-modelo-de-domínio-complexo-para-operações-crud-simples)).
7. **Prioridade:** Alta.

### HEX-02 Casos de uso implementados diretamente em controladores

1. **Problema:** orquestração de negócio, cálculos financeiros, chamadas a repositórios e controle `@Transactional` dentro de `@RestController` ou serviços gRPC (`*ImplBase`).
2. **Como identificar:** controllers injetando portas de saída/repositórios diretamente, contendo condicionais de regra de negócio ou anotados com `@Transactional`.
3. **Consequências:** duplicação da regra ao expor a mesma funcionalidade via REST e gRPC; impossibilidade de testar o fluxo sem a camada de transporte.
4. **Exemplo problemático:**
   ```java
   @PostMapping("/transfers")
   @Transactional
   public ResponseEntity<Void> transfer(@RequestBody TransferRequest req) {
       var source = accountRepo.findById(req.sourceId()).orElseThrow();
       source.debit(req.amount());
       accountRepo.save(source);
       return ResponseEntity.ok().build();
   }
   ```
5. **Alternativa recomendada:** mover a orquestração e o `@Transactional` para `TransferMoneyUseCase` na camada `application` e restringir o controller à conversão de protocolo.
6. **Exceções:** endpoints de leitura trivial em módulos que não utilizam arquitetura hexagonal.
7. **Prioridade:** Alta.

### HEX-03 Portas sem necessidade real ou interfaces 1:1 indiscriminadas

1. **Problema:** criar interfaces para todas as classes internas (casos de uso sem polimorfismo, mapeadores, calculadoras em memória), gerando pares `*Port`/`*Service` + `*Impl` desnecessários.
2. **Como identificar:** interfaces com única implementação para componentes que não realizam I/O nem cruzam fronteiras de módulos (ex.: `AccountMapper` + `AccountMapperImpl`).
3. **Consequências:** duplicação de assinaturas, indireção sem valor e maior custo de manutenção.
4. **Exemplo problemático:**
   ```java
   public interface InterestCalculator { Money calculate(Money principal); }
   public class InterestCalculatorImpl implements InterestCalculator { ... }
   ```
5. **Alternativa recomendada:** usar classes concretas para casos de uso, serviços de domínio e mapeadores internos; reservar interfaces para portas de saída (I/O) ou polimorfismo real de negócio.
6. **Exceções:** interfaces exigidas pelo Spring Data (`JpaRepository`) ou convenções preexistentes no pacote alterado.
7. **Prioridade:** Baixa.

### HEX-04 Serviços genéricos que acumulam múltiplas responsabilidades

1. **Problema:** uma única classe (`AccountService`, `FinanceManager`) concentra dezenas de casos de uso distintos, regras de domínio, queries complexas e integrações externas (*God Service*).
2. **Como identificar:** classes de serviço com mais de 5–6 dependências no construtor e métodos públicos sem coesão entre si (abrir conta, transferir, fechar mês, exportar PDF).
3. **Consequências:** alto acoplamento, testes complexos e risco elevado de regressão ao alterar qualquer método.
4. **Exemplo problemático:**
   ```java
   @Service
   public class AccountService {
       // 8 dependências injetadas
       public void openAccount(...) { ... }
       public void transfer(...) { ... }
       public byte[] generateAnnualTaxPdf(...) { ... }
   }
   ```
5. **Alternativa recomendada:** separar em classes de casos de uso focadas (`OpenAccountUseCase`, `TransferMoneyUseCase`, `GenerateAnnualTaxReportQuery`), injetando apenas as portas que cada operação realmente utiliza.
6. **Exceções:** agrupar operações CRUD pequenas e fortemente coesas de um mesmo subdomínio simples.
7. **Prioridade:** Média.

### HEX-05 Entidades anêmicas em domínios ricos em regras

1. **Problema:** entidades de domínio contendo apenas campos com `getters` e `setters` públicos, deixando todas as validações de invariantes e cálculos nos serviços de aplicação ([Fowler, AnemicDomainModel](https://martinfowler.com/bliki/AnemicDomainModel.html)).
2. **Como identificar:** classes em `domain` com `@Data`/`@Setter` ou `setBalance(...)`/`setStatus(...)` públicos, e casos de uso manipulando atributos diretamente (`account.setBalance(account.getBalance().subtract(val))`).
3. **Consequências:** invariantes violadas quando um novo caso de uso altera o estado esquecendo de repetir a validação; lógica financeira duplicada.
4. **Exemplo problemático:**
   ```java
   // No caso de uso:
   if (account.getStatus() != AccountStatus.ACTIVE) throw new AccountInactiveException();
   account.setBalance(account.getBalance().subtract(cmd.amount()));
   ```
5. **Alternativa recomendada:** substituir `setters` públicos por métodos de comando na entidade (`account.debit(cmd.amount())`) que validem internamente o status e o saldo antes de mutar o estado.
6. **Exceções:** entidades de persistência (`*JpaEntity`), DTOs e projeções de leitura.
7. **Prioridade:** Média (Alta em mutações de saldo e estado financeiro).

### HEX-06 Regras de negócio espalhadas por controladores e adaptadores

1. **Problema:** implementar partes de uma regra de negócio dentro de adaptadores (ex.: cálculo de tarifa no controller ou política de aprovação automática dentro do `PersistenceAdapter`).
2. **Como identificar:** cálculos de `BigDecimal`, regras de elegibilidade ou alteração condicional de status de negócio dentro de `adapter.in.*` ou `adapter.out.*`.
3. **Consequências:** comportamento divergente conforme o canal de entrada ou saída; impossibilidade de testar a regra completa no teste unitário de domínio.
4. **Exemplo problemático:**
   ```java
   // Dentro do AccountPersistenceAdapter:
   if (account.balance().amount().compareTo(new BigDecimal("50000")) > 0) {
       jpaEntity.setTier("VIP"); // Regra de negócio escondida no adaptador de banco
   }
   ```
5. **Alternativa recomendada:** calcular a classificação/regra dentro da entidade `Account` no domínio e fazer o adaptador apenas persistir o estado resultante.
6. **Exceções:** validação sintática de entrada no adaptador web (`@NotBlank`, `@Positive`) e constraints do PostgreSQL (`UNIQUE`, `CHECK`, `FOREIGN KEY`) como defesa em profundidade.
7. **Prioridade:** Alta.

### HEX-07 Portas de saída que expõem tipos de ORM ou banco

1. **Problema:** assinaturas de portas de saída que expõem `Page`, `Pageable` ou `Specification` do Spring Data, `EntityManager` do JPA ou `Result`/`Record` do jOOQ.
2. **Como identificar:** `import org.springframework.data.*` ou `import org.jooq.*` em interfaces do pacote `application` ou `domain`.
3. **Consequências:** vaza a tecnologia de persistência para os casos de uso e obriga testes unitários a construir objetos de infraestrutura.
4. **Exemplo problemático:**
   ```java
   public interface TransactionRepositoryPort {
       org.springframework.data.domain.Page<Transaction> findAll(Pageable pageable);
   }
   ```
5. **Alternativa recomendada:** usar tipos próprios enxutos na porta (`PageQuery(int page, int size)` e `PagedResult<T>(List<T> items, long total)`) e converter para `PageRequest.of(...)` dentro do adaptador de saída.
6. **Exceções:** módulos não hexagonais que utilizam `JpaRepository` diretamente na camada de serviço.
7. **Prioridade:** Média.

### HEX-08 Entidades de persistência reutilizadas como domínio e contrato HTTP

1. **Problema:** usar a mesma classe `@Entity` como tabela do banco, modelo de domínio e payload `@RequestBody`/resposta em controllers REST.
2. **Como identificar:** métodos de `@RestController` recebendo ou retornando classes anotadas com `@Entity` (`jakarta.persistence.Entity`).
3. **Consequências:** risco de *Mass Assignment* (adulteração de campos internos pelo cliente HTTP), `LazyInitializationException` na serialização JSON, quebra de contrato da API ao alterar colunas do banco e incapacidade de proteger invariantes devido ao construtor vazio e `setters` exigidos pelo ORM.
4. **Exemplo problemático:**
   ```java
   @PostMapping("/entries")
   public LedgerEntryEntity create(@RequestBody LedgerEntryEntity entity) {
       return jpaRepository.save(entity);
   }
   ```
5. **Alternativa recomendada:** receber um `record` DTO no controller, converter para comando/domínio e usar a `@Entity` apenas dentro do adaptador de persistência.
6. **Exceções:** nenhuma para exposição direta de `@Entity` na camada HTTP/gRPC.
7. **Prioridade:** Alta (Crítica quando expõe campos financeiros ou de permissão à sobrescrita via *Mass Assignment*).

### HEX-09 DTOs e mapeamentos redundantes sem benefício claro

1. **Problema:** criar múltiplos DTOs e mapeadores intermediários idênticos sem mudança de semântica nem transição real de fronteira (ex.: `RequestDto` -> `ControllerDto` -> `UseCaseCommand` -> `DomainInputDto`).
2. **Como identificar:** três ou mais `records`/classes com os mesmos campos sendo copiados em sequência na mesma requisição.
3. **Consequências:** código repetitivo, risco de esquecer campos no mapeamento e custo de manutenção sem ganho arquitetural.
4. **Exemplo problemático:** converter `AccountSummaryProjection` (retornado pelo jOOQ na porta de leitura) para `AccountSummaryUseCaseDto` e depois para `AccountSummaryHttpResponse` quando os três possuem exatamente `(UUID id, BigDecimal balance)`.
5. **Alternativa recomendada:** usar apenas as conversões que cruzam fronteiras com modelos incompatíveis:
   - Na **escrita:** `RequestDto` (transporte) -> `Command` (aplicação) -> `Entidade` (domínio) -> `JpaEntity` (persistência).
   - Na **leitura:** permitir que o `record` de projeção retornado pela porta de consulta seja retornado diretamente pelo caso de uso e serializado pelo controller quando o contrato JSON for idêntico.
6. **Exceções:** quando o contrato externo exige estrutura ou nomenclatura distinta da projeção interna.
7. **Prioridade:** Média.

### HEX-10 Acoplamento entre casos de uso e tecnologias externas

1. **Problema:** casos de uso que dependem diretamente de `RestClient`, stubs gRPC (`*BlockingStub`), clientes de mensageria ou SDKs externos sem porta de saída.
2. **Como identificar:** imports de `org.springframework.web.client.*`, `io.grpc.*` ou pacotes gerados por Protobuf dentro do pacote `application`.
3. **Consequências:** mudanças no protocolo ou contrato externo quebram o caso de uso; testes unitários do caso de uso exigem mockar clientes de rede complexos.
4. **Exemplo problemático:**
   ```java
   public class TransferMoneyUseCase {
       private final AntiFraudServiceGrpc.AntiFraudServiceBlockingStub grpcStub;
   }
   ```
5. **Alternativa recomendada:** extrair uma porta de saída orientada ao negócio (`AntiFraudCheckPort`) na camada `application` e encapsular o stub gRPC em um adaptador em `adapter.out.client`.
6. **Exceções:** nenhuma em módulos hexagonais.
7. **Prioridade:** Alta.

### HEX-11 Dependências circulares entre módulos ou pacotes

1. **Problema:** ciclos de importação entre pacotes arquiteturais (ex.: `application` importa tipo de `adapter` enquanto `adapter` importa `application`) ou entre contextos de negócio (`account` <-> `billing`).
2. **Como identificar:** imports mútuos entre pacotes irmãos ou de camadas internas para externas.
3. **Consequências:** impossibilita evolução, teste isolado e futura extração de módulos.
4. **Exemplo problemático:** `account.application.TransferMoneyUseCase` importando `account.adapter.in.web.TransferRequest`.
5. **Alternativa recomendada:** mover o tipo de entrada/saída para `application` (`TransferMoneyCommand`) para que o adaptador dependa da aplicação, nunca o inverso. Entre contextos distintos, use uma porta de saída ou eventos.
6. **Exceções:** nenhuma.
7. **Prioridade:** Alta.

### HEX-12 Interfaces excessivamente genéricas ou abstrações especulativas

1. **Problema:** definir interfaces genéricas como `UseCase<I, O>` ou `GenericRepositoryPort<T, ID>` com métodos padronizados (`findAll`, `deleteById`) para todas as entidades.
2. **Como identificar:** casos de uso forçados a usar `Void` (retornando `null`) para caber em `UseCase<Command, Void>`; portas de repositório expondo `deleteById` ou `findAll` para entidades financeiras imutáveis ou volumosas.
3. **Consequências:** perda de expressividade da linguagem de negócio e exposição de operações perigosas ou proibidas (como exclusão física de lançamentos contábeis).
4. **Exemplo problemático:**
   ```java
   public interface GenericRepositoryPort<T, ID> {
       T save(T entity);
       List<T> findAll();
       void deleteById(ID id);
   }
   ```
5. **Alternativa recomendada:** criar contratos explícitos e restritos por agregado (`LedgerEntryRepositoryPort` apenas com `append(LedgerEntry)` e `findById(UUID)`).
6. **Exceções:** nenhuma para repositórios de domínio.
7. **Prioridade:** Média.

### HEX-13 Modelo de domínio complexo para operações CRUD simples

1. **Problema:** aplicar separação completa entre entidade de domínio, entidade JPA, múltiplos mapeadores, comandos e portas em cadastros triviais sem invariantes nem cálculos de negócio.
2. **Como identificar:** classes em `domain` que são cópias 1:1 das colunas da tabela, sem nenhum método de comportamento ou validação de negócio.
3. **Consequências:** excesso de arquivos e código de mapeamento sem benefício de testabilidade ou proteção de regras.
4. **Exemplo problemático:** criar 8 classes e interfaces em 4 subpacotes apenas para cadastrar e listar uma tabela auxiliar `CostCenter(id, code, name)`.
5. **Alternativa recomendada:** para subdomínios puramente cadastrais (CRUD), usar um fluxo direto (`Controller` -> `Service` -> `JpaRepository`/jOOQ com DTO `record`), reservando a arquitetura hexagonal para fluxos com regras de negócio relevantes.
6. **Exceções:** quando o usuário solicitar explicitamente a estrutura hexagonal naquele módulo.
7. **Prioridade:** Média.

### HEX-14 Transações abertas além do limite necessário

1. **Problema:** manter `@Transactional` aberto no caso de uso enquanto portas de saída realizam chamadas HTTP (`RestClient`), gRPC ou I/O de rede externo.
2. **Como identificar:** método `@Transactional` que invoca tanto portas de banco de dados quanto portas de clientes remotos/notificações (ver também [PER-01](./JAVA_BEST_PRACTICES.md#per-01-consultas-n1-io-em-laços-e-transações-longas)).
3. **Consequências:** esgotamento do pool de conexões HikariCP sob latência de rede e inconsistência entre estado persistido e efeitos colaterais externos.
4. **Exemplo problemático:**
   ```java
   @Transactional
   public void execute(TransferMoneyCommand cmd) {
       var tax = externalTaxClientPort.fetchTax(cmd.amount()); // Rede externa na transação!
       // ... debita e salva no banco
   }
   ```
5. **Alternativa recomendada:** consultar APIs externas *antes* de abrir a transação de banco, executar a gravação transacional em escopo mínimo (`TransactionTemplate` ou método transacional dedicado) e acionar notificações externas *após* o commit (ou via *Transactional Outbox*).
6. **Exceções:** quando todas as portas chamadas no método operam exclusivamente sobre o mesmo banco de dados local.
7. **Prioridade:** Alta.

### HEX-15 Testes de unidade que exigem infraestrutura real

1. **Problema:** subir `@SpringBootTest` ou Testcontainers para testar regras de cálculo do domínio ou orquestração de casos de uso.
2. **Como identificar:** `@SpringBootTest` em testes que verificam apenas regras de saldo, tarifas, vencimentos ou fluxos de `UseCase` (ver [TST-02](./JAVA_BEST_PRACTICES.md#tst-02-testes-frágeis-lentos-ou-acoplados-à-implementação)).
3. **Consequências:** execução lenta (segundos/minutos em vez de milissegundos) e baixa cobertura de casos de borda.
4. **Exemplo problemático:** usar `@SpringBootTest` para validar que `Account.debit(...)` rejeita valores maiores que o saldo.
5. **Alternativa recomendada:** testar classes de `domain` e `application` com JUnit 5 puro, instanciando-as diretamente com `new` e usando implementações *Fake* em memória para as portas de saída.
6. **Exceções:** testes de integração de adaptadores e o teste de fumaça do contexto (`GestaoFinanceiraApplicationTests`).
7. **Prioridade:** Média.

### HEX-16 Testes baseados exclusivamente em mocks nos adaptadores

1. **Problema:** testar adaptadores de saída de persistência (`*PersistenceAdapter`) mockando `JpaRepository`, `EntityManager` ou `DSLContext` em vez de usar um banco PostgreSQL real.
2. **Como identificar:** `@Mock SpringDataAccountRepository` ou `@Mock DSLContext` em testes de classes de `adapter.out.persistence`.
3. **Consequências:** falsos positivos — o teste passa, mas o mapeamento JPA, a query jOOQ ou a constraint das migrações Flyway falha no PostgreSQL em execução real.
4. **Exemplo problemático:** verificar apenas `verify(jpaRepository).save(any())` no teste do `AccountPersistenceAdapter`.
5. **Alternativa recomendada:** testar adaptadores de persistência com `@DataJpaTest` ou `@JooqTest` combinados com `@Import(TestcontainersConfiguration.class)` contra o contêiner PostgreSQL real configurado no projeto.
6. **Exceções:** simular falhas transitórias de rede/driver difíceis de reproduzir via contêiner.
7. **Prioridade:** Alta.

### HEX-17 Eventos, mediadores ou camadas adicionais sem necessidade concreta

1. **Problema:** introduzir barramentos de comando (*Mediator* em memória), eventos internos ou camadas extras para chamadas síncronas diretas dentro do mesmo contexto.
2. **Como identificar:** controller ou caso de uso publicando evento em `ApplicationEventPublisher` apenas para acionar um único listener síncrono obrigatório no mesmo pacote.
3. **Consequências:** fluxo de controle oculto, perda de verificação estática de retorno e dificuldade de rastreamento e depuração.
4. **Exemplo problemático:** `eventPublisher.publishEvent(new ExecuteDebitInternalCommand(id, amount))` em vez de chamar o caso de uso ou método diretamente.
5. **Alternativa recomendada:** realizar chamadas de método diretas e explícitas; usar eventos apenas para desacoplar múltiplos consumidores independentes, integrar contextos distintos ou agendar ações pós-commit (`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`).
6. **Exceções:** ações assíncronas ou pós-commit genuínas.
7. **Prioridade:** Média.

---

## 7. Estratégia de testes

| Alvo do teste | Escopo e objetivo | Ferramentas e padrão de implementação |
| --- | --- | --- |
| **1. Domínio** (`domain`) | Validar invariantes, cálculos (`BigDecimal`), transições de estado e objetos de valor | **JUnit 5 + AssertJ puros.** Zero Spring, zero mocks, zero containers. Use datas fixas (`Instant.parse(...)`). |
| **2. Casos de Uso** (`application`) | Validar orquestração de negócio, tratamento de resultados e interação com portas de saída | **JUnit 5 + AssertJ.** Instancie o caso de uso via construtor passando `Clock.fixed(...)`, *Fakes* em memória (`Map<UUID, Account>`) para portas de repositório e mocks/spies pontuais apenas para portas de notificação externa. |
| **3. Adaptador de Entrada** (`adapter.in`) | Validar rotas HTTP, serialização JSON, Bean Validation (`@Valid`), tradução de `Outcome`/exceções para HTTP `ProblemDetail` ou status gRPC | **`@WebMvcTest`** (ou teste de serviço gRPC) mockando o caso de uso. Não suba o banco de dados. |
| **4. Adaptador de Saída** (`adapter.out`) | Validar entidades JPA, queries SQL/jOOQ, migrações Flyway, constraints do PostgreSQL e clientes HTTP/gRPC | **`@DataJpaTest` / `@JooqTest` + `@Import(TestcontainersConfiguration.class)`** (PostgreSQL real). Para clientes HTTP/gRPC, use servidor local de teste (`MockRestServiceServer` / servidor gRPC in-process). |
| **5. Fumaça / Composição** | Validar que o contexto Spring completo e as conexões de infraestrutura sobem sem conflitos de beans | **`@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`** (ex.: `GestaoFinanceiraApplicationTests`). |
| **6. Arquitetura** | Garantir ausência de imports proibidos entre `domain`, `application` e `adapter` | Verificação manual dos `imports` conforme a [Matriz de dependência (seção 3)](#3-regras-e-matriz-de-dependência) (o `pom.xml` atual não inclui ArchUnit; não adicione dependências sem solicitação). |

---

## 8. Referências

- Alistair Cockburn — [Hexagonal Architecture (Ports and Adapters)](https://alistair.cockburn.us/hexagonal-architecture)
- Martin Fowler — [AnemicDomainModel](https://martinfowler.com/bliki/AnemicDomainModel.html)
- [Java SE 25 Documentation](https://docs.oracle.com/en/java/javase/25/) e [Java Language Updates (Java 25)](https://docs.oracle.com/en/java/javase/25/language/java-language-changes.html)
- [JEP 395: Records](https://openjdk.org/jeps/395), [JEP 409: Sealed Classes](https://openjdk.org/jeps/409), [JEP 440: Record Patterns](https://openjdk.org/jeps/440), [JEP 441: Pattern Matching for switch](https://openjdk.org/jeps/441), [JEP 456: Unnamed Variables & Patterns](https://openjdk.org/jeps/456) e [JEP 513: Flexible Constructor Bodies](https://openjdk.org/jeps/513)
- [Spring Framework Reference — Core & Data Access](https://docs.spring.io/spring-framework/reference/) e [Spring Boot Reference](https://docs.spring.io/spring-boot/index.html)
- [Jakarta Persistence Specification](https://jakarta.ee/specifications/persistence/), [jOOQ Manual](https://www.jooq.org/doc/latest/manual/) e [Testcontainers for Java](https://java.testcontainers.org/)
