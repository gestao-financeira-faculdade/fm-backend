# Padrões de Arquitetura Backend (Spring Boot) e Versionamento Git (Stacked PRs)

Estas regras consolidam os padrões arquiteturais (Clean Architecture, Clean Code e SOLID), de documentação e de versionamento exigidos em todas as implementações e refatorações do projeto:

## 1. Controllers Interface-Driven, Clean Architecture & SOLID (SRP / DIP)
- **Separação em Interfaces (`*ControllerOpenApi`)**: Toda classe `@RestController` deve obrigatoriamente implementar uma interface dedicada (ex.: `AuthControllerOpenApi`, `FinancialEntryControllerOpenApi`, `ReportExportControllerOpenApi`).
- **Anotações Exclusivas na Interface**:
  - Todas as anotações de roteamento e binding do Spring Web (`@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`, `@PathVariable`, `@RequestParam`, `@RequestHeader`, `@RequestBody`, `@DateTimeFormat`, `@Valid`) devem ficar **exclusivamente na interface**.
  - Todas as anotações do Swagger/OpenAPI (`@Tag`, `@Operation`, `@ApiResponses`, `@ApiResponse`, `@Parameter`, `@SecurityRequirement`) devem ficar **exclusivamente na interface**, descrevendo detalhadamente o objetivo, payloads de entrada, formatos de saída e todos os códigos de erro HTTP (`ProblemDetail` RFC 9457).
- **Classes Concretas Limpas (`@RestController`) e Desacoplamento Estrito de Camadas**:
  - A classe concreta deve conter apenas `@RestController` (e `@PreAuthorize` quando aplicável) e os métodos `@Override` enxutos, delegando imediatamente para Use Cases e Web Mappers.
  - **Proibição de `record` / DTOs Internos na Controller**: Nunca declare `record` ou classes de request/response dentro da Controller ou da interface OpenAPI. Todo DTO deve residir em seu próprio arquivo dentro do pacote dedicado `adapter.in.web.dto`.
  - **Proibição de Métodos Auxiliares de Mapeamento ou Construção de Response na Controller**: Nunca crie métodos privados de conversão (`toDomain`, `toResponse`, `firstNonBlank`) ou montagem de `ResponseEntity`/headers (`buildExportResponse`) dentro da Controller. Extraia toda tradução HTTP/Domínio e construção de respostas para uma camada dedicada de mapeamento web (`adapter.in.web.mapper.*WebMapper`).
  - **Inversão de Dependência (DIP)**: Controllers nunca devem injetar ou conhecer adapters de saída (ex.: `CsvStreamingExporter`, `PdfReportExporter`, `S3Client`, repositórios). Toda coordenação de regras e formatos pertence à camada `application` (Use Cases).
  - **Tratamento Global de Erros**: Nunca acople métodos `@ExceptionHandler` dentro de controllers (todo tratamento de exceções pertence ao `GlobalExceptionHandler`).
- **Regra Contínua**: Sempre que criar um novo endpoint ou uma nova controller, aplique obrigatoriamente este padrão de interface OpenAPI + DTOs em `adapter.in.web.dto` + Mapper em `adapter.in.web.mapper` + Use Case em `application` + classe concreta limpa.

## 2. Separação Estrita entre `UseCase` e `Service` (Proibido `*UseCaseService`)
- **`UseCase` (`*UseCase`)**: Determina um **fluxo específico e atômico de negócio** dentro do sistema (ex.: `CreateFinancialEntryUseCase`, `SettleFinancialEntryUseCase`, `CancelFinancialEntryUseCase`, `UploadFiscalAttachmentUseCase`, `CreateCategoryUseCase`). Cada `UseCase` tem responsabilidade única (**SRP**) sobre aquele fluxo.
- **`Service` (`*Service`)**: Pode ser apenas um **orquestrador de múltiplos casos de uso** ou um componente técnico encarregado de **ações que dependem de algo externo** (ex.: `JwtTokenService`).
- **Proibição de Mistura Conceitual**: Nunca crie classes híbridas como `*UseCaseService` nem agrupe múltiplos casos de uso independentes (criar, editar, listar, cancelar) dentro de um único Service monolítico.

## 3. Roteamento Global (`/api`) e Versionamento (`/v1`)
- O prefixo global `/api` é configurado exclusivamente no `application.yaml` via `server.servlet.context-path: /api`.
- O prefixo de versão `/v1` deve ser declarado no `@RequestMapping("/v1...")` da interface da Controller (e nas rotas relativas do `SecurityConfig`). Nunca repita `/api/v1` nas anotações de métodos.

## 4. Centralização de Variáveis de Ambiente (`ApplicationProperties`)
- Nunca espalhe `@Value` por controllers, adapters ou classes de configuração (`SecurityConfig`, gRPC, Storage) e nunca utilize URLs/URIs hardcoded no código.
- Centralize todas as propriedades injetadas via `@Value("${...}")` na classe `ApplicationProperties` (`@Getter @Component`).
- **Valores Default Exclusivos no `application.yaml`**: Os valores default devem existir apenas no `application.yaml` (`${ENV_VAR:default}`). Não crie constantes estáticas `DEFAULT_*` redundantes nem repita fallbacks `:default` dentro das anotações `@Value` na classe Java.

## 5. Estratégia de Validação por Camada (Jakarta Bean Validation, Hexagonal Architecture & CQRS)
De acordo com as documentações de referência do **Spring Framework**, **Jakarta Bean Validation 3.0 (Hibernate Validator — JSR 380)** e as boas práticas de **Arquitetura Hexagonal e CQRS**, cada camada possui um mecanismo de validação próprio:
- **1. Validação Sintática / Estrutural de Entrada (`DTOs Web` e `Commands` / `Queries` na borda `application`)**:
  - **Onde usar Jakarta Bean Validation (`@Validated`, `@Valid`, `@NotBlank`, `@NotNull`, `@Email`, `@Size`, `@Positive`)**: Nos **DTOs de Request** (validados na interface `*ControllerOpenApi`) e nos objetos **`Command` / `Query`** recebidos pelos Use Cases (`@Validated` na classe do Use Case validando `@Valid Command`).
  - **Regra Crítica da Especificação Jakarta Bean Validation (§4.5.5 — `HV000151`)**: Quando uma classe implementa uma interface (como `*Controller implements *ControllerOpenApi` ou `*Adapter implements *Port`), as anotações de constraint de parâmetro (`@Valid`, `@NotBlank`, `@NotNull`) devem ser declaradas **exclusivamente no método da interface**, **nunca** repetidas ou adicionadas nos métodos `@Override` da classe concreta (o que viola o Princípio de Substituição de Liskov na especificação JSR-380 e dispara `ConstraintDeclarationException: HV000151` em runtime).
- **2. Validação de Invariantes de Domínio (`Entidades` e `Value Objects` na camada `domain`)**:
  - **Não utilize `@Validated` do Spring em Entidades/Value Objects de domínio**, pois eles não são beans gerenciados pelo container IoC (não passam por proxy AOP) e o domínio deve permanecer desacoplado de infraestrutura. Garanta as invariantes nos construtores compactos (`record`) e Factory Methods lançando exceções de domínio (`BusinessRuleViolationException`, `InvalidArgumentDomainException`).
- **3. Validação Semântica / Regras de Negócio (`Use Cases` na camada `application`)**:
  - Regras que dependem de estado persistido ou cruzamento entre agregados (ex.: conta ativa, compatibilidade de categoria, exigência de anexo fiscal acima de R$ 5.000) devem ser validadas via métodos de domínio ou **Validation Strategies (`List<ValidationStrategy>`)**, retornando erros semânticos (`422` / `409`).
- **4. Portas de Saída (`Outbound Ports`) e Adapters de Infraestrutura**:
  - Evite espalhar `@Validated` em Adapters de Saída quando o `Command` e a Entidade de Domínio já foram validados na entrada do Use Case. Caso uma Porta de Saída exija contrato declarativo de pré-condição (`@NotNull` / `@NotBlank`), anote **apenas a interface `*Port`**, nunca o método `@Override` no Adapter concreto.

## 6. Clean Code: Early Return e Tamanho Máximo de Métodos (<= 25 Linhas)
- **Preferência por *Early Return* (*Guard Clauses*)**: Em cenários simples de validação, fallback ou inicialização condicional, prefira sempre o retorno antecipado (*early return*) em vez de blocos `if/else` aninhados, reduzindo a indentação ciclomática e destacando o fluxo principal.
- **Limite de 25 Linhas por Método**: Nenhum método deve ultrapassar **25 linhas**. Quando um método acumular múltiplas etapas (como preparação de dados, execução de algoritmo/loop e montagem de resposta), extraia métodos auxiliares privados coesos, pequenos e com nomes semânticos claros.

## 7. Infraestrutura Local e Persistência Adequada por Caso de Uso
- **Tokens Efêmeros no Redis**: Refresh Tokens e sessões revogáveis devem ser armazenados no **Redis** (`spring-boot-starter-data-redis`) com TTL nativo, e não no PostgreSQL.
- **Object Storage com S3 e MiniStack**: O armazenamento de anexos e comprovantes deve usar **AWS S3 (`S3Client`)** apontando localmente para o **MiniStack (`ministackorg/ministack`, porta `4566`)** no `docker-compose.yaml`, nunca `LocalFileSystem`.

## 8. Fluxo de Versionamento Git e Stacked Pull Requests
- **Conventional Commits Atômicos**: Utilize `<PREFIX>(<SCOPE>): <Message>` em inglês, no imperativo e sem ponto final.
- **Proibição Estrita de `cherry-pick`**: Quando uma branch depender do código de outra, é **estritamente proibido** o uso de `git cherry-pick`.
- **Stacked PRs no GitHub**: Edite as Pull Requests diretamente no GitHub alterando a `base branch` para que a PR dependente aponte para a branch da PR de origem (ex.: `main <- feat/shared <- feat/identity <- feat/audit <- feat/entry <- feat/simulation <- feat/reporting`).
- **Sincronização Exclusiva com `git merge`**: Para atualizar e propagar alterações entre branches dependentes, utilize exclusivamente `git merge <branch-origem>` localmente.
