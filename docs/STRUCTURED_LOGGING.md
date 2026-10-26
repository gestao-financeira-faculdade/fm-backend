# Guia de Observabilidade: Structured Logging (Java 25 / Spring Boot 4)

Referência operacional para implementação, manutenção e revisão de logs estruturados neste repositório. Complementa [JAVA_BEST_PRACTICES.md](./JAVA_BEST_PRACTICES.md) e [HEXAGONAL_ARCHITECTURE.md](./HEXAGONAL_ARCHITECTURE.md).

- **Escopo:** instrumentação de logs em código Java (`*.java`), configuração do SLF4J / Logback no Spring Boot 4 e integração com a stack de observabilidade do projeto (**OpenTelemetry**, **Micrometer Tracing**, **datasource-micrometer** e **Grafana Loki** definidos em [docker-compose.yaml](../docker-compose.yaml) e [TestcontainersConfiguration.java](../src/test/java/com/emanudev/gestaofinanceira/TestcontainersConfiguration.java)).

## Índice

1. [Regras de decisão para o agente](#1-regras-de-decisão-para-o-agente)
2. [Padrão de estrutura e formato JSON](#2-padrão-de-estrutura-e-formato-json)
3. [Chaves obrigatórias e contextuais](#3-chaves-obrigatórias-e-contextuais)
4. [Boas práticas de implementação em Java 25 e Spring Boot](#4-boas-práticas-de-implementação-em-java-25-e-spring-boot)
   - [4.1 API fluente do SLF4J 2 (`atInfo`, `addKeyValue`)](#41-api-fluente-do-slf4j-2-atinfo-addkeyvalue)
   - [4.2 Onde logar na Arquitetura Hexagonal](#42-onde-logar-na-arquitetura-hexagonal)
   - [4.3 Níveis de severidade (`ERROR`, `WARN`, `INFO`, `DEBUG`)](#43-níveis-de-severidade-error-warn-info-debug)
   - [4.4 Tratamento de exceções e stack traces](#44-tratamento-de-exceções-e-stack-traces)
5. [Segurança, LGPD e mascaramento de dados financeiros](#5-segurança-lgpd-e-mascaramento-de-dados-financeiros)
6. [Rastreabilidade ponta a ponta (Traceability e Correlação)](#6-rastreabilidade-ponta-a-ponta-traceability-e-correlação)
7. [Anti-patterns de logging](#7-anti-patterns-de-logging)
   - [LOG-01 Concatenação de strings e mensagens não estruturadas](#log-01-concatenação-de-strings-e-mensagens-não-estruturadas)
   - [LOG-02 Vazamento de segredos, PII ou dados financeiros sensíveis](#log-02-vazamento-de-segredos-pii-ou-dados-financeiros-sensíveis)
   - [LOG-03 Log-and-Throw (duplicação de logs da mesma exceção)](#log-03-log-and-throw-duplicação-de-logs-da-mesma-exceção)
   - [LOG-04 Uso de System.out, System.err ou printStackTrace](#log-04-uso-de-systemout-systemerr-ou-printstacktrace)
   - [LOG-05 Manipulação manual de MDC sem limpeza ou incompatível com Virtual Threads](#log-05-manipulação-manual-de-mdc-sem-limpeza-ou-incompatível-com-virtual-threads)
   - [LOG-06 Ruído excessivo em laços ou consultas de alta frequência](#log-06-ruído-excessivo-em-laços-ou-consultas-de-alta-frequência)
8. [Referências](#8-referências)

---

## 1. Regras de decisão para o agente

1. **Todo log deve ser estruturado e orientado a eventos:** trate cada linha de log como um registro JSON indexável e consultável no Grafana Loki, contendo uma mensagem estática concisa e atributos chave-valor tipados (`addKeyValue`).
2. **Use a API fluente do SLF4J 2.x:** prefira `log.atInfo().addKeyValue("account_id", accountId).log("Account debited")` em vez de interpolar variáveis soltas no texto da mensagem.
3. **Respeite o isolamento do domínio:** classes do pacote `domain` (entidades, objetos de valor, serviços de domínio puros) **não devem realizar logging de infraestrutura**. O registro de eventos operacionais e falhas ocorre nos **casos de uso (`application`)** e nos **adaptadores (`adapter.in` / `adapter.out`)**.
4. **Propagação automática de rastreamento:** nunca gere `trace_id` manualmente com `UUID.randomUUID()` nem gerencie `ThreadLocal` por conta própria. Utilize o contexto automático do **Spring Boot OpenTelemetry / Micrometer Tracing**, que injeta `trace_id` e `span_id` no MDC em requisições HTTP, chamadas gRPC e queries SQL (`datasource-micrometer`).
5. **Proteção de dados sensíveis (Prioridade Crítica):** jamais registre senhas, tokens JWT, chaves de API, números completos de cartão, CPF/CNPJ sem máscara ou payloads brutos de requisições de autenticação.
6. **Evite *Log-and-Throw*:** não faça `log.error(...)` dentro de um adaptador de saída ou caso de uso se a exceção for relançada para ser tratada e logada pelo `@RestControllerAdvice` ou interceptor gRPC global.

---

## 2. Padrão de estrutura e formato JSON

No Spring Boot 4 (3.4+), utilize o suporte nativo a **Structured Logging** (`spring.logging.structured.format.console=ecs` ou `logstash`, ou exportação OTLP para o coletor OpenTelemetry / Grafana LGTM) para que cada evento seja emitido em **uma única linha JSON válida (NDJSON)** no `stdout`.

**Exemplo de payload JSON emitido pela aplicação:**

```json
{
  "@timestamp": "2026-10-26T18:55:12.418392Z",
  "level": "INFO",
  "service.name": "business-finance",
  "service.version": "0.0.1-SNAPSHOT",
  "logger": "com.emanudev.gestaofinanceira.account.application.TransferMoneyUseCase",
  "thread": "VirtualThread[#64]/runnable@ForkJoinPool-1-worker-3",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7",
  "event.action": "transfer.completed",
  "account_source_id": "0192c8a4-7b12-7000-8123-456789abcdef",
  "account_target_id": "0192c8a4-7b15-7000-9abc-def012345678",
  "currency": "BRL",
  "idempotency_key": "req-99281-ab",
  "message": "Financial transfer executed successfully"
}
```

### Regras de formatação do JSON

- **`@timestamp` / `timestamp`:** sempre em formato ISO-8601 UTC (`Z`), com precisão mínima de milissegundos.
- **Nomes de chaves:** sempre em `snake_case` minúsculo para atributos de negócio (`account_id`, `transfer_id`, `idempotency_key`, `error_code`) ou seguindo a convenção semântica do OpenTelemetry (`service.name`, `event.action`).
- **Mensagem (`message`):** texto curto, estático e descritivo em inglês, permitindo agrupar eventos idênticos no Loki por template de mensagem enquanto os filtros utilizam os campos estruturados.
- **Uma linha por evento:** stack traces de exceções são serializados dentro do campo estruturado `error.stack_trace` (ou `stack_trace`), evitando que uma única exceção quebre em dezenas de linhas órfãs no coletor de logs.

---

## 3. Chaves obrigatórias e contextuais

### 3.1 Chaves obrigatórias (injetadas pela plataforma / framework)

| Chave JSON | Origem | Descrição |
| --- | --- | --- |
| `@timestamp` / `timestamp` | Logback / Spring Structured Logging | Instante exato do evento em UTC (ISO-8601). |
| `level` | SLF4J | Severidade do evento (`ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`). |
| `service.name` | `spring.application.name` (`business-finance`) | Identificador do serviço no ecossistema de observabilidade. |
| `trace_id` | OpenTelemetry / Micrometer Tracing (MDC) | ID global da transação distribuída (32 caracteres hexadecimais W3C Trace Context). |
| `span_id` | OpenTelemetry / Micrometer Tracing (MDC) | ID da unidade de trabalho/operação atual (16 caracteres hexadecimais). |
| `logger` | SLF4J | Nome qualificado da classe que emitiu o log. |
| `thread` | JVM | Nome da thread (incluindo identificação de Virtual Thread no Java 25). |
| `message` | Código da aplicação | Descrição textual estática e concisa do evento. |

### 3.2 Chaves contextuais de negócio (adicionadas via `addKeyValue`)

Adicione apenas as chaves pertinentes à operação em execução:

| Chave recomendada | Tipo | Quando incluir |
| --- | --- | --- |
| `event.action` | `String` | Ação de negócio sendo executada (ex.: `account.created`, `transfer.completed`, `transfer.rejected`). |
| `account_id` | `UUID` / `String` | Operações sobre uma conta específica. |
| `transaction_id` / `entry_id` | `UUID` / `String` | Criação, consulta ou estorno de lançamentos financeiros. |
| `idempotency_key` | `String` | Comandos de mutação financeira que recebem chave de idempotência. |
| `currency` | `String` | Código ISO-4217 da moeda (`BRL`, `USD`) em operações monetárias. |
| `outcome` / `reason` | `String` | Motivo estruturado de rejeição de regra de negócio (ex.: `INSUFFICIENT_FUNDS`, `ACCOUNT_BLOCKED`). |
| `duration_ms` | `long` | Tempo de execução de integrações externas ou processamentos em lote relevantes. |
| `error.type` / `error.message` | `String` | Preenchidos automaticamente pelo encoder ao passar o `Throwable` ao logger. |

---

## 4. Boas práticas de implementação em Java 25 e Spring Boot

### 4.1 API fluente do SLF4J 2 (`atInfo`, `addKeyValue`)

O Spring Boot 4 inclui o **SLF4J 2.x**, cuja API fluente anexa pares chave-valor diretamente ao `LoggingEvent`. O serializador de logs estruturados do Spring Boot converte cada `addKeyValue(k, v)` em um campo de primeiro nível no JSON final.

```java
package com.emanudev.gestaofinanceira.account.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferMoneyUseCase {

    private static final Logger log = LoggerFactory.getLogger(TransferMoneyUseCase.class);
    private final AccountRepositoryPort accountRepository;

    public TransferMoneyUseCase(AccountRepositoryPort accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public TransferOutcome execute(TransferMoneyCommand command) {
        var source = accountRepository.findById(command.sourceAccountId())
                .orElseThrow(() -> new AccountNotFoundException(command.sourceAccountId()));

        if (source.balance().isLessThan(command.amount())) {
            log.atInfo()
               .addKeyValue("event.action", "transfer.rejected")
               .addKeyValue("account_source_id", command.sourceAccountId())
               .addKeyValue("reason", "INSUFFICIENT_FUNDS")
               .addKeyValue("idempotency_key", command.idempotencyKey())
               .log("Transfer rejected by domain policy");
            return new TransferOutcome.InsufficientFunds(command.amount().subtract(source.balance()));
        }

        // ... executa débito, crédito e persistência
        log.atInfo()
           .addKeyValue("event.action", "transfer.completed")
           .addKeyValue("account_source_id", command.sourceAccountId())
           .addKeyValue("account_target_id", command.targetAccountId())
           .addKeyValue("currency", command.amount().currency().getCurrencyCode())
           .addKeyValue("idempotency_key", command.idempotencyKey())
           .log("Financial transfer executed successfully");

        return new TransferOutcome.Completed(transferId, processedAt);
    }
}
```

- **Avaliação preguiçosa (*Lazy Evaluation*):** quando a construção de um valor para `DEBUG` ou `TRACE` tiver custo computacional, use o `Supplier` na API fluente:
  ```java
  log.atDebug()
     .addKeyValue("payload_size", () -> computePayloadSize(request))
     .log("Inbound request received");
  ```

### 4.2 Onde logar na Arquitetura Hexagonal

| Camada | Deve logar? | O que registrar |
| --- | --- | --- |
| **`domain`** | **Não** | Nenhuma dependência de `org.slf4j.*`. Entidades e objetos de valor comunicam resultados via retorno de método, objetos de resultado (`sealed interface`) ou exceções de domínio. |
| **`application`** | **Sim** | Início/conclusão de casos de uso críticos de mutação financeira (`INFO`), rejeições de negócio relevantes para auditoria operacional (`INFO` ou `WARN`) e conflitos de idempotência. |
| **`adapter.in`** | **Sim (nas bordas)** | Falhas de validação de entrada (`WARN` em `@RestControllerAdvice` / interceptor gRPC) e erros não tratados (`ERROR` com `Throwable` no handler global). |
| **`adapter.out`** | **Sim (integrações)** | Falhas de comunicação com APIs externas, retentativas, circuit breakers abertos (`WARN`/`ERROR`) e conflitos de integridade/concorrência otimista traduzidos. Queries SQL já são instrumentadas pelo `datasource-micrometer`. |

### 4.3 Níveis de severidade (`ERROR`, `WARN`, `INFO`, `DEBUG`)

| Nível | Critério operacional | Exemplo no sistema financeiro |
| --- | --- | --- |
| **`ERROR`** | Falha inesperada de sistema ou infraestrutura que impediu o cumprimento de uma operação e exige atenção/alerta. | Banco PostgreSQL indisponível, falha não recuperável em integração gRPC/HTTP crítica, exceção não tratada (`500 Internal Server Error`). |
| **`WARN`** | Anomalia recuperável, degradação temporária, tentativa inválida suspeita ou conflito de concorrência (`@Version`). | `OptimisticLockingFailureException` ao atualizar saldo concorrentemente, timeout com fallback acionado, requisição com chave de idempotência duplicada em andamento. |
| **`INFO`** | Eventos de negócio significativos e marcos do ciclo de vida da aplicação. | Conta criada, transferência concluída, fechamento contábil processado, rejeição de negócio relevante. |
| **`DEBUG`** | Detalhes técnicos de fluxo úteis apenas para diagnóstico em desenvolvimento ou troubleshooting temporário. | Parâmetros de paginação/filtros de consulta jOOQ, detalhes de serialização de adaptadores. Desabilitado por padrão em produção. |

### 4.4 Tratamento de exceções e stack traces

Ao registrar uma exceção na fronteira de erro (como `@RestControllerAdvice` ou interceptor gRPC), passe a instância `Throwable` sempre via `.setCause(exception)` na API fluente (ou como último argumento do método tradicional):

```java
log.atError()
   .setCause(ex)
   .addKeyValue("event.action", "http.request.failed")
   .addKeyValue("http.path", request.getRequestURI())
   .addKeyValue("http.method", request.getMethod())
   .log("Unhandled exception processing HTTP request");
```

---

## 5. Segurança, LGPD e mascaramento de dados financeiros

Logs costumam ser retidos por longos períodos no Grafana Loki e acessados por múltiplos perfis técnicos. Aplique estas restrições estritas:

| Categoria de dado | Regra em logs | Como proceder se precisar de correlação |
| --- | --- | --- |
| **Segredos e Credenciais** (`password`, `Authorization`, `Bearer`, `api_key`, `secret`) | **Proibição absoluta** | Nunca logar em nenhum nível (nem `DEBUG`/`TRACE`). |
| **Dados Pessoais (PII / LGPD)** (CPF, CNPJ, e-mail, telefone, nome completo, endereço) | **Proibido em texto claro** | Use exclusivamente o identificador interno (`owner_id`, `user_id` em `UUID`) ou versão mascarada (`***456789-00`). |
| **Dados de Pagamento / Cartão / Chave PIX** | **Proibido em texto claro** | Logar apenas identificadores internos da transação (`transaction_id`) ou os 4 últimos dígitos quando estritamente necessário. |
| **Saldos e Valores Monetários Individuais** | **Restrito** | Em logs operacionais (`INFO`), prefira registrar `transaction_id`, `account_id`, `currency` e `event.action` sem expor o saldo total da conta do cliente em texto aberto nos logs de infraestrutura. |

---

## 6. Rastreabilidade ponta a ponta (Traceability e Correlação)

A arquitetura de observabilidade do projeto conecta **Métricas (Prometheus)**, **Traces (OpenTelemetry)** e **Logs (Grafana Loki)** dentro do stack LGTM (`grafana/otel-lgtm` nos testes e containers dedicados no `docker-compose.yaml`):

```text
[Requisição REST / Chamada gRPC]
       │  (Extrai ou gera W3C traceparent: trace_id + span_id)
       ▼
[Adaptador de Entrada (WebMVC / gRPC)]
       │  (Micrometer Tracing injeta trace_id e span_id no contexto/MDC)
       ▼
[Caso de Uso (Application)] ──► Emite Log JSON com trace_id, span_id, account_id, idempotency_key
       │
       ▼
[Adaptador de Saída (JPA / jOOQ / RestClient / gRPC Client)]
       ├──► datasource-micrometer cria Span SQL com o mesmo trace_id
       └──► RestClient / gRPC propaga cabeçalho traceparent para serviços externos
```

### Como isso viabiliza o diagnóstico rápido

1. **Correlação Alerta -> Trace -> Log:** ao investigar uma métrica de latência alta ou erro 5xx no Grafana (Prometheus), o engenheiro acessa o exemplar com `trace_id` e salta diretamente para todos os logs no **Grafana Loki** que possuem `{service_name="business-finance"} | json | trace_id="<id>"`.
2. **Correlação por Entidade ou Idempotência:** quando um cliente reporta falha em uma operação financeira, é possível consultar no Loki diretamente por `idempotency_key` ou `account_id`, obtendo o `trace_id` correspondente e inspecionando inclusive as queries SQL executadas no PostgreSQL (`datasource-micrometer`).
3. **Execução assíncrona e Virtual Threads:** sempre que delegar tarefas assíncronas, utilize executores gerenciados pelo Spring (com `ContextPropagatingTaskDecorator` / instrumentação do Micrometer) para que o `trace_id` seja propagado automaticamente para a Virtual Thread filha.

---

## 7. Anti-patterns de logging

### LOG-01 Concatenação de strings e mensagens não estruturadas

1. **Problema:** concatenar variáveis diretamente na string de log (`"Transferencia de " + amount + " da conta " + sourceId + " para " + targetId`).
2. **Como identificar:** operador `+`, `String.format(...)` ou `.formatted(...)` dentro de chamadas `log.info(...)`, `log.warn(...)`, `log.error(...)` ou `log.debug(...)`.
3. **Consequências:** alocação desnecessária de strings mesmo quando o nível de log está desabilitado; impede filtragem e agregação eficiente por campo no Grafana Loki (exige regex custoso sobre texto livre em vez de filtro indexado sobre chave JSON).
4. **Evite:**
   ```java
   log.info("Account " + accountId + " failed transfer with reason: " + reason);
   ```
5. **Prefira:**
   ```java
   log.atInfo()
      .addKeyValue("event.action", "transfer.rejected")
      .addKeyValue("account_id", accountId)
      .addKeyValue("reason", reason)
      .log("Transfer rejected");
   ```
6. **Prioridade:** Média.

### LOG-02 Vazamento de segredos, PII ou dados financeiros sensíveis

1. **Problema:** passar objetos inteiros de requisição (`log.info("Request: {}", request)`), entidades de usuário ou cabeçalhos HTTP diretamente para o log.
2. **Como identificar:** `addKeyValue("request", req)` ou `log.debug("Headers: {}", headers)` onde o objeto contém tokens, senhas, documentos (CPF/CNPJ) ou dados bancários completos.
3. **Consequências:** violação grave de segurança e conformidade (LGPD/PCI), expondo credenciais e dados financeiros a qualquer pessoa com acesso de leitura ao Grafana/Loki.
4. **Alternativa recomendada:** selecionar explicitamente apenas os campos seguros e necessários (`account_id`, `idempotency_key`, `currency`) via `addKeyValue`.
5. **Prioridade:** Crítica.

### LOG-03 Log-and-Throw (duplicação de logs da mesma exceção)

1. **Problema:** capturar uma exceção, fazer `log.error(...)` e imediatamente relançar a mesma exceção (ou uma exceção encapsulada), fazendo com que ela seja logada novamente no caso de uso e no `@RestControllerAdvice`.
2. **Como identificar:** blocos `catch (Exception e) { log.error("...", e); throw e; }`.
3. **Consequências:** um único erro real gera 2 ou 3 registros de `ERROR` com o mesmo stack trace e o mesmo `trace_id`, distorcendo métricas e alertas de taxa de erro no Grafana/Prometheus.
4. **Alternativa recomendada:** deixar a exceção subir enriquecida com contexto de negócio (na própria exceção customizada) e registrá-la **uma única vez** no handler global do adaptador de entrada (`@RestControllerAdvice` ou interceptor gRPC).
5. **Prioridade:** Média.

### LOG-04 Uso de System.out, System.err ou printStackTrace

1. **Problema:** escrever diretamente na saída padrão ou usar `e.printStackTrace()`.
2. **Como identificar:** chamadas a `System.out.print*`, `System.err.print*`, `IO.println` ou `Throwable.printStackTrace()` no código da aplicação.
3. **Consequências:** emite texto puro fora do formato JSON estruturado, sem `timestamp`, sem `trace_id`, sem nível de severidade e quebrando o parser de JSON dos coletores de log.
4. **Alternativa recomendada:** usar sempre o `Logger` do SLF4J com a API fluente estruturada.
5. **Prioridade:** Alta.

### LOG-05 Manipulação manual de MDC sem limpeza ou incompatível com Virtual Threads

1. **Problema:** chamar `MDC.put("key", value)` manualmente em serviços ou filtros sem garantir `MDC.remove("key")` em bloco `finally`, ou criar `ThreadLocal` customizado para guardar dados de log.
2. **Como identificar:** chamadas diretas a `org.slf4j.MDC.put(...)` sem `try-with-resources` (`MDC.putCloseable`) ou sem `finally`.
3. **Consequências:** vazamento de atributos de contexto de uma requisição para outra quando threads são reutilizadas ou perda de contexto em execuções assíncronas.
4. **Alternativa recomendada:** preferir `.addKeyValue(key, value)` por evento (que não muta o estado da thread) ou, quando um atributo precisar valer para um escopo inteiro na mesma thread, usar `try (var _ = MDC.putCloseable("key", value)) { ... }` (aproveitando variáveis não nomeadas `_` da [JEP 456](https://openjdk.org/jeps/456)).
5. **Prioridade:** Alta.

### LOG-06 Ruído excessivo em laços ou consultas de alta frequência

1. **Problema:** emitir um log `INFO` dentro de um laço `for` / `Stream` que processa centenas ou milhares de lançamentos financeiros.
2. **Como identificar:** `log.info(...)` ou `log.atInfo()` dentro de `for`, `while` ou `.forEach(...)`.
3. **Consequências:** inundação do Grafana Loki (*log flooding*), degradação severa de I/O e aumento de latência da aplicação.
4. **Alternativa recomendada:** emitir um único log `INFO` consolidado ao final do processamento em lote com contadores agregados (`processed_count`, `failed_count`, `duration_ms`).
5. **Prioridade:** Alta.

---

## 8. Referências

- [Spring Boot Reference — Structured Logging](https://docs.spring.io/spring-boot/reference/features/logging.html#features.logging.structured)
- [SLF4J 2.0 Fluent Logging API Manual](https://www.slf4j.org/manual.html#fluent)
- [OpenTelemetry Logs & Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/general/logs/)
- [Grafana Loki — Best Practices for Structured Logs](https://grafana.com/docs/loki/latest/get-started/labels/)
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
