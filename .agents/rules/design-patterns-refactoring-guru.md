# Catálogo de Design Patterns (Refactoring.Guru) e Separação Conceitual UseCase vs. Service

Esta rule orienta o agente a **escolher o Design Pattern adequado com base estritamente no caso de uso e no problema arquitetural real** (sem forçar um único padrão para todos os cenários) e estabelece a **distinção obrigatória entre `UseCase` e `Service`**.

---

## 1. Distinção Estrita: `UseCase` vs. `Service` (Proibido `*UseCaseService`)

Nunca misture os conceitos de **Caso de Uso (`UseCase`)** e **Serviço (`Service`)**:

| Conceito | Responsabilidade Única | Exemplos Corretos | Anti-Patterns Proibidos |
| :--- | :--- | :--- | :--- |
| **`UseCase` (`*UseCase`)** | Determina um **fluxo específico e atômico de negócio** dentro do sistema (criar, liquidar, cancelar, buscar, exportar). Cada fluxo de negócio é uma classe independente com um único método público de entrada (`execute`). | `CreateFinancialEntryUseCase`, `SettleFinancialEntryUseCase`, `CancelFinancialEntryUseCase`, `UploadFiscalAttachmentUseCase`, `AuthenticateUserUseCase` | `FinancialEntryUseCaseService` (mistura os dois conceitos), ou colocar criar/editar/deletar/listar na mesma classe. |
| **`Service` (`*Service`)** | Atua como um **orquestrador de múltiplos casos de uso** ou como um **provedor de ações técnicas que dependem de recursos externos/infraestrutura**. | `JwtTokenService` (assinatura/validação criptográfica de tokens), ou um serviço que compõe 2+ `UseCase`s distintos. | `ReferenceDataService` acumulando CRUDs inteiros de múltiplas entidades sem separação em `UseCase`s. |

---

## 2. Princípio de Seleção de Design Patterns Orientada ao Caso de Uso

**Não aplique um único Design Pattern de forma mecânica.** A escolha do padrão (ou da combinação de padrões) deve nascer da **natureza do caso de uso**:

| Natureza do Caso de Uso / Problema no Domínio | Design Pattern Indicado | Por que escolher este padrão para o caso de uso? |
| :--- | :--- | :--- |
| O caso de uso precisa instanciar um Value Object ou Entidade validando invariantes semânticas (ex.: criar moeda em centavos vs. valor decimal, ou criar lançamento inicialmente vencido vs. pendente) | **Factory Method** | Expressa a intenção do caso de uso pelo nome do método (`Money.of(...)`, `Money.zero(...)`, `FinancialEntry.create(...)`) e protege as invariantes antes do objeto nascer. |
| O caso de uso constrói objetos complexos com muitas opções, filtros opcionais ou mensagens de protocolo (ex.: requisição de simulação Monte Carlo gRPC) | **Builder** | Evita construtores telescópicos e permite montagem fluente e imutável passo a passo. |
| O caso de uso precisa trocar uma família inteira de conectores relacionados conforme o ambiente (ex.: suíte de mensageria + storage + segredos Cloud vs. Local) | **Abstract Factory** | Garante compatibilidade entre objetos da mesma família de infraestrutura sem acoplar o caso de uso às classes concretas. |
| O caso de uso precisa transicionar o estado de uma entidade imutável preservando os demais dados originais | **Prototype** | Gera novas instâncias imutáveis derivadas do estado atual (`record` copy / métodos de transição na entidade). |
| O caso de uso precisa conversar com um banco de dados, bucket S3, Redis, motor gRPC ou biblioteca externa sem contaminar o domínio | **Adapter (Ports & Adapters)** | Traduz o contrato da tecnologia externa para a Porta (`*Port`) definida pelo domínio/aplicação. |
| O caso de uso exige adicionar comportamento transversal (auditoria, controle de concorrência com semáforo, cache, métricas, resiliência) sem poluir a regra de negócio | **Decorator** ou **Proxy** | Envolve o componente original mantendo o mesmo contrato (`@Aspect`, `@Transactional`, ou Decorator explícito na Porta). |
| O caso de uso possui duas dimensões independentes que variam ao mesmo tempo (ex.: Tipo de Relatório Contábil × Formato de Renderização) | **Bridge** | Evita a explosão combinatória de subclasses desacoplando a abstração da implementação. |
| O caso de uso manipula estruturas em árvore onde partes e todo são tratados igualmente (ex.: linhagem de splits pai/filhos ou plano de contas hierárquico) | **Composite** | Permite operações recursivas uniformes sobre nós folhas e nós compostos. |
| O caso de uso oferece uma entrada simplificada para um subsistema complexo (ex.: coordenação de criptografia, compressão e upload multipart) | **Facade** | Reduz o acoplamento do cliente com as classes internas do subsistema. |
| O caso de uso seleciona **um algoritmo específico em tempo de execução** com base em um tipo/formato/modalidade (ex.: exportar `CSV` vs. `PDF`, liquidar `FULL` vs. `PARTIAL`) | **Strategy + Factory** | Substitui `if/else` ou `switch` por polimorfismo. No Spring Boot, a `Factory` injeta `Set<Strategy>` em um `Map<Key, Strategy>` ([referência `vsouzx/Strategy-And-Factory-Pattern`](https://github.com/vsouzx/Strategy-And-Factory-Pattern)). |
| O caso de uso precisa executar **um conjunto extensível de regras de validação independentes** sobre o mesmo comando | **Composite Validation Strategy (`List<ValidationStrategy>`)** | Substitui sequências de `if (...) throw ...` no Use Case por classes de regra isoladas e testáveis injetadas como `List<ValidationStrategy>`. |
| O caso de uso processa uma requisição em etapas onde cada elo pode resolver/interromper ou delegar ao próximo (ex.: filtros HTTP, resolução de IP/CorrelationId com fallback) | **Chain of Responsibility** | Desacopla o remetente dos múltiplos destinatários potenciais em uma fila ordenada de handlers. |
| O caso de uso representa uma intenção de mutação com parâmetros validados, chave de idempotência e metadados de auditoria | **Command** | Encapsula todos os dados de entrada do `UseCase` em um objeto imutável (`CreateEntryCommand`, `SettlementCommand`) validado via Jakarta Bean Validation. |
| O comportamento de uma entidade muda completamente conforme seu estado atual no ciclo de vida (`PENDING`, `OVERDUE`, `PAID`, `CANCELLED`) | **State** | Encapsula as regras de transição permitidas e proibidas dentro do próprio estado, eliminando `if (status == ...)` espalhados. |
| A conclusão de um caso de uso deve notificar outros módulos (ex.: atualizar projeção, registrar auditoria assíncrona, disparar alerta) sem acoplamento direto | **Observer** (Domain Events) | Permite que múltiplos *listeners* reajam ao evento publicado sem que o `UseCase` emissor precise conhecê-los. |
| O caso de uso precisa gravar e preservar um snapshot imutável do estado anterior e posterior de uma entidade para auditoria/compliance | **Memento** | Captura o estado interno (`previousState` / `newState`) sem violar o encapsulamento da entidade. |
| Dois ou mais algoritmos compartilham exatamente o mesmo fluxo de etapas (esqueleto), variando apenas na implementação de 1 ou 2 passos | **Template Method** | Evita duplicação do fluxo estrutural mantendo os pontos de extensão bem definidos. |
| O caso de uso percorre grandes coleções de dados (ex.: milhões de lançamentos para relatório) sem estourar a memória heap | **Iterator** | Consome dados sob demanda via cursores/`Stream` lazy. |

---

## 3. Catálogo Detalhado dos 22 Design Patterns (Refactoring.Guru) e Implementação

### 3.1. Padrões Criacionais (Creational Patterns)

1. **Factory Method**
   - **Problema:** Construtores públicos não comunicam a diferença semântica entre variações de criação e dificultam a validação prévia.
   - **Implementação:** Métodos estáticos de fábrica com nomes de domínio (`Money.brl("100.00")`, `Money.zero("BRL")`, `FinancialEntry.create(...)`, `SettlementCommand.full(...)`, `SettlementCommand.partialWithSplit(...)`).
2. **Abstract Factory**
   - **Problema:** O sistema precisa instanciar conjuntos de adaptadores coesos por família de provedor sem `if (provider.equals("AWS"))`.
   - **Implementação:** Interface `StorageProviderFactory` produzindo os clientes compatíveis daquele ambiente via `@Configuration` / `@Profile`.
3. **Builder**
   - **Problema:** Objetos com muitos parâmetros (especialmente opcionais) geram chamadas ilegíveis e propensas a erro de ordem de argumentos.
   - **Implementação:** Builders fluentes em contratos gRPC/Protobuf (`RiskSimulationRequest.newBuilder()...build()`) ou builders internos em objetos complexos.
4. **Prototype**
   - **Problema:** Criar uma nova versão de uma entidade imutável copiando seus 20 atributos manualmente é verboso e sujeito a falhas.
   - **Implementação:** Métodos de evolução de estado na própria entidade imutável que retornam uma nova instância preservando a identidade e histórico (`entry.markOverdue(...)`, `entry.cancel(...)`).
5. **Singleton**
   - **Problema:** Componentes stateless (Use Cases, Mappers, Factories, Adapters) não devem ser recriados a cada requisição, mas também não podem usar estado global estático mutável.
   - **Implementação:** Delegue **100% ao container IoC do Spring** (`@Component`, `@Service`, `@Repository`). Nunca crie singletons manuais com `getInstance()` estático.

---

### 3.2. Padrões Estruturais (Structural Patterns)

6. **Adapter (Wrapper)**
   - **Problema:** O núcleo de negócio não pode depender de SDKs da AWS (`S3Client`), comandos Redis (`StringRedisTemplate`), entidades JPA ou stubs gRPC.
   - **Implementação:** A camada `application`/`domain` define uma interface (`AttachmentStoragePort`, `RefreshTokenRepositoryPort`, `RiskSimulationEnginePort`) e a camada `adapter.out` implementa essa interface encapsulando a tecnologia externa.
7. **Bridge**
   - **Problema:** Acoplar o tipo de documento/relatório ao formato de arquivo gera classes `RelatorioXFormatoY` em crescimento exponencial.
   - **Implementação:** Separe a preparação/agregação dos dados do relatório (`UseCase` + modelo de domínio) da estratégia de renderização (`ReportExportStrategy`).
8. **Composite**
   - **Problema:** Lançamentos financeiros que sofreram *split* parcial formam uma árvore de linhagem (`rootEntryId` -> `parentEntryId` -> filhos residuais) que precisa ser consultada e validada como uma unidade.
   - **Implementação:** Estruturas hierárquicas recursivas (`FinancialEntryLineageNode`) e composição de regras de validação.
9. **Decorator**
   - **Problema:** Adicionar resiliência, controle de concorrência, cache ou compressão diretamente dentro do código de negócio viola o **SRP**.
   - **Implementação:** Envolver uma porta ou stream com um objeto que implementa a mesma interface e adiciona o comportamento antes/depois de delegar.
10. **Facade**
    - **Problema:** Um fluxo externo precisaria conhecer e chamar vários componentes de baixo nível na ordem certa.
    - **Implementação:** Expor uma interface coesa de alto nível que encapsula a complexidade do subsistema.
11. **Flyweight**
    - **Problema:** Instanciar repetidamente objetos imutáveis idênticos (formatadores, constantes monetárias zero, configurações imutáveis) desperdiça memória.
    - **Implementação:** Reutilizar constantes imutáveis thread-safe (`Money.ZERO_BRL`, `DateTimeFormatter.ISO_LOCAL_DATE`).
12. **Proxy**
    - **Problema:** Controle de acesso (`@PreAuthorize`), transações (`@Transactional`) e captura de trilha de auditoria (`@AuditedFinancialAction`) não devem se repetir manualmente em cada método.
    - **Implementação:** Proxies dinâmicos do Spring AOP (`FinancialAuditAspect`) interceptando anotações declarativas.

---

### 3.3. Padrões Comportamentais (Behavioral Patterns)

13. **Chain of Responsibility**
    - **Problema:** Uma requisição precisa passar por uma cadeia ordenada de verificações ou extrações com fallback (ex.: filtros de segurança/correlação HTTP, resolução de IP do cliente em múltiplos headers).
    - **Implementação:** `OncePerRequestFilter` (`CorrelationMdcFilter`) ou cadeia ordenada de extratores onde o primeiro elo capaz de resolver retorna o resultado.
14. **Command**
    - **Problema:** Passar 10 parâmetros soltos para o método `execute` de um `UseCase` dificulta a leitura, evolução e validação declarativa.
    - **Implementação:** Todo `UseCase` de escrita recebe um `record` imutável `*Command` (ex.: `CreateEntryCommand`, `SettlementCommand`, `AuthenticateCommand`) anotado com Jakarta Bean Validation (`@NotNull`, `@NotBlank`, `@Positive`).
15. **Iterator**
    - **Problema:** Carregar coleções gigantescas de uma só vez causa `OutOfMemoryError`.
    - **Implementação:** Paginação (`PageQuery` / `PagedResult`) e leitura em streaming.
16. **Mediator**
    - **Problema:** Se repositórios, validadores, calculadoras e auditores conversarem diretamente entre si, forma-se uma teia acoplada impossível de testar.
    - **Implementação:** O `UseCase` atua como o mediador daquele fluxo específico, coordenando a entidade de domínio e as portas de saída.
17. **Memento**
    - **Problema:** Conformidade financeira exige registrar exatamente como o registro estava antes (`previousState`) e como ficou depois (`newState`) de cada mutação.
    - **Implementação:** Snapshots imutáveis serializados no `AuditLogEntry` via `AuditTrailPort.recordTransition(...)`.
18. **Observer**
    - **Problema:** Disparar ações reativas (ex.: varredura de títulos vencidos no startup da aplicação ou reação a eventos de domínio) sem acoplar o produtor aos consumidores.
    - **Implementação:** `@EventListener` / `ApplicationEventPublisher` do Spring.
19. **State**
    - **Problema:** Regras como *"só pode liquidar se estiver PENDING ou OVERDUE"* e *"não pode cancelar se já estiver PAID/RECEIVED"* geram `if/else` repetidos se ficarem fora do modelo de estado.
    - **Implementação:** Comportamento encapsulado no próprio `EntryStatus` (`isOpen()`, `isSettled()`, `isTerminal()`, `terminalStatusFor(EntryType)`) e nas transições da entidade `FinancialEntry`.
20. **Strategy (isolado ou combinado com Factory / List)**
    - **Problema:** Um caso de uso possui algoritmos intercambiáveis (ex.: exportação `CSV` vs. `PDF`, persistência de liquidação `FULL` vs. `PARTIAL`, detecção de assinatura `PDF`/`JPEG`/`PNG`, ou múltiplas regras de validação de negócio).
    - **Implementação:**
      - **Seleção de 1 estratégia por chave (`Strategy + Factory`)**: Interface `Strategy` + implementações `@Component` + `Factory` (`@Component`) que recebe `Set<Strategy>` e indexa em um `Map<Key, Strategy>` ([modelo `strategy-example-1` de `vsouzx/Strategy-And-Factory-Pattern`](https://github.com/vsouzx/Strategy-And-Factory-Pattern)).
      - **Execução de múltiplas estratégias de validação (`List<ValidationStrategy>`)**: Interface `*ValidationStrategy` + implementações `@Component` (`@Order`) + injeção de `List<*ValidationStrategy>` executada via `.forEach(...)` ([modelo `strategy-example-2` de `vsouzx/Strategy-And-Factory-Pattern`](https://github.com/vsouzx/Strategy-And-Factory-Pattern)).
      - **Isolamento do Domínio (`HEX-01`)**: Estratégias anotadas com `@Component` / `@Order` do Spring devem residir na camada `application.strategy` (ou `adapter`). Estratégias que residem dentro do pacote `domain` (ex.: validação pura de invariantes binárias) devem ser **100% Java SE 25 puro**, sem nenhum import de `org.springframework.*`.
21. **Template Method**
    - **Problema:** Algoritmos que têm etapas invariantes na mesma ordem, diferindo apenas em ganchos específicos.
    - **Implementação:** Métodos `default` em interfaces ou classes base enxutas que definem o fluxo fixo e delegam etapas abstratas.
22. **Visitor**
    - **Problema:** Executar operações distintas sobre uma hierarquia fechada de tipos sem poluir as classes de dados.
    - **Implementação:** No Java 21+, utilize `sealed interface` com Pattern Matching em `switch` exaustivo.
