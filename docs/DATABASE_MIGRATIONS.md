# Guia de Migrações de Banco de Dados (Flyway e PostgreSQL)

Referência operacional para agentes de IA ao criar, revisar ou evoluir migrações SQL do Flyway e esquemas PostgreSQL neste repositório. Complementa [JAVA_BEST_PRACTICES.md](./JAVA_BEST_PRACTICES.md) e [HEXAGONAL_ARCHITECTURE.md](./HEXAGONAL_ARCHITECTURE.md).

- **Escopo:** scripts SQL em `src/main/resources/db/migration/` e alinhamento entre o esquema PostgreSQL e os adaptadores de persistência (Spring Data JPA/Hibernate e jOOQ).

## Índice

1. [Regras de decisão para o agente](#1-regras-de-decisão-para-o-agente)
2. [Nomenclatura e organização de arquivos Flyway](#2-nomenclatura-e-organização-de-arquivos-flyway)
3. [Convenções de nomenclatura no PostgreSQL](#3-convenções-de-nomenclatura-no-postgresql)
4. [Mapeamento de tipos (PostgreSQL <-> Java 25)](#4-mapeamento-de-tipos-postgresql---java-25)
5. [Armadilhas não óbvias (PostgreSQL + Hibernate + Domínio Financeiro)](#5-armadilhas-não-óbvias-postgresql--hibernate--domínio-financeiro)
   - [5.1 Proibição de CHAR(n) com Hibernate validate (bpchar vs. varchar)](#51-proibição-de-charn-com-hibernate-validate-bpchar-vs-varchar)
   - [5.2 Coluna updated_at não atualiza sozinha em UPDATE no PostgreSQL](#52-coluna-updated_at-não-atualiza-sozinha-em-update-no-postgresql)
   - [5.3 Unicidade condicional (Partial Unique Index) e NULLS NOT DISTINCT](#53-unicidade-condicional-partial-unique-index-e-nulls-not-distinct)
   - [5.4 Chaves de idempotência no banco para mutações financeiras](#54-chaves-de-idempotência-no-banco-para-mutações-financeiras)
   - [5.5 Localidade de índice B-Tree: UUIDv7 vs. UUIDv4](#55-localidade-de-índice-b-tree-uuidv7-vs-uuidv4)
6. [Modelo de referência para criação de tabelas](#6-modelo-de-referência-para-criação-de-tabelas)
7. [Evolução segura de esquema e prevenção de filas de lock](#7-evolução-segura-de-esquema-e-prevenção-de-filas-de-lock)
8. [Anti-patterns em migrações](#8-anti-patterns-em-migrações)
   - [MIG-01 Modificar ou apagar migração já commitada](#mig-01-modificar-ou-apagar-migração-já-commitada)
   - [MIG-02 Ponto flutuante ou tipo MONEY para valores financeiros](#mig-02-ponto-flutuante-ou-tipo-money-para-valores-financeiros)
   - [MIG-03 Chaves estrangeiras sem índice explícito](#mig-03-chaves-estrangeiras-sem-índice-explícito)
   - [MIG-04 TIMESTAMP sem fuso horário para carimbos de tempo](#mig-04-timestamp-sem-fuso-horário-para-carimbos-de-tempo)
   - [MIG-05 ON DELETE CASCADE em registros financeiros e constraints anônimas](#mig-05-on-delete-cascade-em-registros-financeiros-e-constraints-anônimas)
   - [MIG-06 Coluna NOT NULL adicionada sem estratégia de transição](#mig-06-coluna-not-null-adicionada-sem-estratégia-de-transição)
   - [MIG-07 Uso de ENUM nativo do PostgreSQL ou ordinal numérico](#mig-07-uso-de-enum-nativo-do-postgresql-ou-ordinal-numérico)
   - [MIG-08 Migração não validada contra PostgreSQL real (Testcontainers)](#mig-08-migração-não-validada-contra-postgresql-real-testcontainers)
9. [Referências](#9-referências)

---

## 1. Regras de decisão para o agente

1. **Imutabilidade de migrações commitadas:** nunca edite, renomeie ou exclua um arquivo `V*__*.sql` que já faça parte do histórico Git (`main` ou commits anteriores compartilhados). Qualquer correção ou alteração de esquema exige um novo arquivo `V<N+1>__*.sql`.
   - *Exceção única:* uma migração criada na própria tarefa/branch atual que ainda não foi mesclada e cuja edição direta seja solicitada pelo usuário.
2. **Inspeção prévia de versão:** antes de criar uma migração, liste os arquivos em `src/main/resources/db/migration/` para identificar o maior número `V<N>` existente e utilize `V<N+1>`.
3. **Fonte única da verdade do esquema:** o esquema do banco é gerenciado exclusivamente pelo Flyway. Nunca configure `spring.jpa.hibernate.ddl-auto` como `create`, `update` ou `create-drop` em código de produção (use apenas `validate` ou `none`).
4. **Coesão por migração:** agrupe em um mesmo arquivo apenas alterações pertencentes ao mesmo contexto ou entrega funcional (ex.: criar `accounts` e seus índices). Não misture contextos não relacionados no mesmo script.
5. **Integridade financeira no banco (defesa em profundidade):** mesmo que o domínio Java valide invariantes, adicione `NOT NULL`, `FOREIGN KEY`, `UNIQUE` e `CHECK` constraints essenciais (como valores positivos, chaves de idempotência e tamanho de código de moeda) diretamente na tabela PostgreSQL.
6. **Validação obrigatória:** sempre que criar ou alterar uma migração, execute os testes de integração com Testcontainers (`TestcontainersConfiguration`) para comprovar que o Flyway aplica o script sem erros no PostgreSQL real e que o mapeamento JPA/jOOQ permanece válido.

---

## 2. Nomenclatura e organização de arquivos Flyway

- **Diretório padrão:** `src/main/resources/db/migration/`
- **Padrão de versão:** sequencial simples, sem zeros à esquerda, separado da descrição por **dois sublinhados (`__`)**:

```text
V<numero>__<verbo_e_descricao_em_snake_case>.sql
```

| Tipo de alteração | Exemplo de nome de arquivo |
| --- | --- |
| Criação de tabela | `V1__create_accounts_table.sql` |
| Criação de tabelas correlatas do mesmo agregado | `V2__create_ledger_entries_table.sql` |
| Adição de coluna | `V3__add_blocked_reason_to_accounts.sql` |
| Adição de índice ou constraint | `V4__add_competence_date_index_to_ledger_entries.sql` |

- **Idioma e formato:** nomes de arquivos, tabelas, colunas, índices e constraints devem ser escritos em **inglês**, em letras minúsculas (`snake_case`), apenas com caracteres ASCII.
- **Migrações repetíveis (`R__*.sql`):** não utilize para tabelas ou alterações estruturais (DDL). Reserve apenas para `VIEW`s ou funções SQL idempotentes (`CREATE OR REPLACE`) se solicitadas.

---

## 3. Convenções de nomenclatura no PostgreSQL

| Elemento | Convenção | Exemplo |
| --- | --- | --- |
| **Tabelas** | Plural, `snake_case`, sem prefixos técnicos (`tb_`, `tbl_`) | `accounts`, `ledger_entries`, `categories` |
| **Colunas** | Singular, `snake_case` | `id`, `account_id`, `amount`, `currency`, `created_at` |
| **Chave Primária (PK)** | Coluna `id`; constraint `pk_<tabela>` | `CONSTRAINT pk_accounts PRIMARY KEY (id)` |
| **Chave Estrangeira (FK)** | Coluna `<tabela_singular>_id`; constraint `fk_<tabela>_<coluna>` | `CONSTRAINT fk_ledger_entries_account_id FOREIGN KEY (account_id) REFERENCES accounts (id)` |
| **Unique Constraint** | `uq_<tabela>_<colunas>` | `CONSTRAINT uq_accounts_number UNIQUE (account_number)` |
| **Check Constraint** | `ck_<tabela>_<regra>` | `CONSTRAINT ck_ledger_entries_positive_amount CHECK (amount > 0)` |
| **Índice** | `idx_<tabela>_<colunas>` | `CREATE INDEX idx_ledger_entries_account_id ON ledger_entries (account_id);` |

- **Sem aspas duplas em identificadores:** não envolva nomes de tabelas ou colunas em `"..."` (evita sensibilidade a maiúsculas/minúsculas no PostgreSQL).
- **Evite palavras reservadas:** não nomeie tabelas ou colunas como `user`, `order`, `transaction`, `group`, `date`, `type` ou `value` (prefira `users`, `financial_transactions`, `ledger_entries`, `entry_type`, `competence_date`).

---

## 4. Mapeamento de tipos (PostgreSQL <-> Java 25)

| Conceito de negócio | Tipo no PostgreSQL | Tipo no Java 25 | Regra / Observação |
| --- | --- | --- | --- |
| **Identificador (PK / FK)** | `UUID` | `java.util.UUID` | Padrão do projeto. Prefira UUIDv7 ordenado no tempo (ver [seção 5.5](#55-localidade-de-índice-b-tree-uuidv7-vs-uuidv4)). |
| **Valor monetário** | `NUMERIC(19, 2)` | `java.math.BigDecimal` | Use `NUMERIC(19, 4)` ou `NUMERIC(19, 6)` para taxas, cotações ou índices fracionários. |
| **Moeda (ISO-4217)** | `VARCHAR(3)` | `java.util.Currency` / `String` | **Nunca use `CHAR(3)`** (ver [seção 5.1](#51-proibição-de-charn-com-hibernate-validate-bpchar-vs-varchar)). |
| **Carimbo de tempo (evento/auditoria)** | `TIMESTAMPTZ` | `java.time.Instant` (ou `OffsetDateTime`) | Armazena em UTC no PostgreSQL e evita ambiguidade de fuso horário. |
| **Data contábil / vencimento** | `DATE` | `java.time.LocalDate` | Sem componente de hora nem fuso horário. |
| **Enum de domínio** | `VARCHAR(30)` a `VARCHAR(50)` | `enum` (`@Enumerated(EnumType.STRING)`) | Adicione `CHECK` constraint quando o conjunto de estados for estático e crítico. |
| **Versão de lock otimista** | `BIGINT NOT NULL DEFAULT 0` | `long` / `Long` (`@Version`) | Obrigatório em agregados sujeitos a atualização concorrente de saldo/estado. |
| **Texto curto limitado** | `VARCHAR(n)` | `String` | Use quando houver limite de domínio ou contrato (ex.: documento, código, e-mail). |
| **Texto livre / observação** | `TEXT` | `String` | No PostgreSQL, `TEXT` e `VARCHAR` têm o mesmo desempenho interno; use `CHECK (char_length(...) <= N)` se precisar limitar tamanho. |
| **Booleano** | `BOOLEAN` | `boolean` / `Boolean` | Sempre `NOT NULL` com `DEFAULT false` ou `DEFAULT true` quando aplicável. |
| **Metadados estruturados** | `JSONB` | `String` / Map / Record | Prefira `JSONB` (binário indexável) em vez de `JSON`. Não use `JSONB` para substituir colunas financeiras relacionais. |

---

## 5. Armadilhas não óbvias (PostgreSQL + Hibernate + Domínio Financeiro)

### 5.1 Proibição de CHAR(n) com Hibernate validate (bpchar vs. varchar)

No PostgreSQL, o tipo de tamanho fixo `CHAR(n)` é representado internamente como `bpchar` (*blank-padded char*). Quando o Hibernate executa a validação de esquema (`ddl-auto=validate`), um atributo `String` padrão em uma `@Entity` espera o tipo SQL `VARCHAR` e lança `SchemaManagementException: wrong column type encountered ... found [bpchar (Types#CHAR)], but expecting [varchar(3) (Types#VARCHAR)]`.

- **Regra:** utilize sempre `VARCHAR(n)` combinado com `CHECK (char_length(col) = n)` quando o comprimento exato for obrigatório (ex.: `currency VARCHAR(3) NOT NULL` com `CHECK (char_length(currency) = 3)`).

### 5.2 Coluna updated_at não atualiza sozinha em UPDATE no PostgreSQL

Diferentemente do MySQL (`ON UPDATE CURRENT_TIMESTAMP`), no PostgreSQL a cláusula `DEFAULT CURRENT_TIMESTAMP` é avaliada **exclusivamente no `INSERT`**. Em comandos `UPDATE`, o banco mantém o valor antigo de `updated_at` se ele não for explicitamente sobrescrito.

- **Regra:** não presuma que o PostgreSQL atualizará `updated_at` sozinho. A camada de aplicação/domínio ou o adaptador de persistência deve atribuir o novo `Instant` (obtido via `java.time.Clock`) em toda operação de atualização.

### 5.3 Unicidade condicional (Partial Unique Index) e NULLS NOT DISTINCT

1. **Registros com inativação/cancelamento lógico:** uma constraint `UNIQUE (owner_id, currency)` tradicional impede abrir uma nova conta ativa se o cliente possuir uma conta antiga com `status = 'CLOSED'`. Para restringir a unicidade apenas a registros ativos, utilize um **índice único parcial** do PostgreSQL:
   ```sql
   CREATE UNIQUE INDEX uq_accounts_active_owner_currency
       ON accounts (owner_id, currency)
       WHERE status = 'ACTIVE';
   ```
2. **Comportamento de `NULL` em `UNIQUE`:** por padrão no SQL, `NULL != NULL`, o que permite inserir múltiplas linhas duplicadas quando uma das colunas da chave única é `NULL`. Se `NULL` precisar ser tratado como valor igual para impedir duplicatas, utilize `UNIQUE NULLS NOT DISTINCT` (suportado no PostgreSQL 15+).

### 5.4 Chaves de idempotência no banco para mutações financeiras

Verificações de duplicidade feitas apenas no código Java (`if (!repository.existsByIdempotencyKey(key))`) sofrem de condição de corrida (*Time-of-Check to Time-of-Use* — TOCTOU) quando duas requisições de pagamento ou transferência idênticas chegam em paralelo.

- **Regra:** tabelas que registram operações financeiras acionadas por API/mensageria devem possuir coluna de idempotência protegida por constraint única no banco (ex.: `CONSTRAINT uq_ledger_entries_account_idempotency UNIQUE (account_id, idempotency_key)`), capturando a violação de unicidade no adaptador de persistência.

### 5.5 Localidade de índice B-Tree: UUIDv7 vs. UUIDv4

Chaves primárias geradas com `UUIDv4` puramente aleatório (`UUID.randomUUID()` ou `gen_random_uuid()`) inserem registros em posições aleatórias da árvore B-Tree do PostgreSQL, causando *page splits* frequentes, fragmentação de páginas (*index bloat*) e perda de eficiência do cache em tabelas de alta escrita como `ledger_entries`.

- **Regra:** mantenha o tipo da coluna como `UUID`, mas prefira **UUIDv7** (ordenado cronologicamente nos primeiros 48 bits). No PostgreSQL 18+ (`postgres:latest`), a função nativa `DEFAULT uuidv7()` está disponível no banco; quando gerado na aplicação Java, gere UUIDs ordenados por tempo nos fluxos de escrita intensa.

---

## 6. Modelo de referência para criação de tabelas

Exemplo completo aplicando todas as convenções do projeto (`V1__create_accounts_and_ledger_entries_tables.sql`):

```sql
CREATE TABLE accounts (
    id UUID NOT NULL DEFAULT uuidv7(),
    owner_id UUID NOT NULL,
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT ck_accounts_currency_length CHECK (char_length(currency) = 3),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED'))
);

CREATE INDEX idx_accounts_owner_id ON accounts (owner_id);

CREATE UNIQUE INDEX uq_accounts_active_owner_currency
    ON accounts (owner_id, currency)
    WHERE status = 'ACTIVE';

CREATE TABLE ledger_entries (
    id UUID NOT NULL DEFAULT uuidv7(),
    account_id UUID NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    competence_date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_ledger_entries PRIMARY KEY (id),
    CONSTRAINT fk_ledger_entries_account_id FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT uq_ledger_entries_account_idempotency UNIQUE (account_id, idempotency_key),
    CONSTRAINT ck_ledger_entries_positive_amount CHECK (amount > 0),
    CONSTRAINT ck_ledger_entries_entry_type CHECK (entry_type IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_ledger_entries_currency_length CHECK (char_length(currency) = 3)
);

CREATE INDEX idx_ledger_entries_account_id_competence_date
    ON ledger_entries (account_id, competence_date DESC);
```

---

## 7. Evolução segura de esquema e prevenção de filas de lock

1. **Transações em DDL no PostgreSQL:** o Flyway executa cada script `V*__*.sql` dentro de uma transação. Se uma instrução falhar, todo o script sofre rollback automaticamente.
2. **Prevenção de fila de bloqueio (*Lock Queue*) em tabelas grandes:**
   - No PostgreSQL, um `ALTER TABLE` solicita lock `ACCESS EXCLUSIVE`. Se houver uma transação longa aberta na tabela, o `ALTER TABLE` entra em espera e **bloqueia todas as leituras (`SELECT`) e escritas subsequentes** atrás dele.
   - Ao adicionar uma `FOREIGN KEY` ou `CHECK` constraint em uma tabela já populada em produção, evite o scan bloqueante completo usando `NOT VALID` seguido de validação online:
     ```sql
     ALTER TABLE ledger_entries
         ADD CONSTRAINT fk_ledger_entries_category_id
         FOREIGN KEY (category_id) REFERENCES categories (id) NOT VALID;

     ALTER TABLE ledger_entries
         VALIDATE CONSTRAINT fk_ledger_entries_category_id;
     ```
3. **Adição de coluna obrigatória (`NOT NULL`) em tabela já existente:**
   - Se houver valor padrão constante, use `ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT ...` (operação instantânea em metadados no PostgreSQL 11+).
   - Se exigir cálculo sobre linhas históricas (*backfill*), separe em: (1) adicionar coluna `NULL`, (2) preencher dados com `UPDATE`, (3) aplicar `SET NOT NULL`.
4. **Remoção ou renomeação de coluna (*Expand and Contract*):** primeiro remova o uso da coluna nas entidades JPA e queries jOOQ da aplicação e somente em migração posterior execute `DROP COLUMN`.

---

## 8. Anti-patterns em migrações

### MIG-01 Modificar ou apagar migração já commitada

1. **Problema:** editar o conteúdo ou o nome de um arquivo `V*__*.sql` que já foi commitado e potencialmente executado em bancos locais, de CI ou de produção.
2. **Como identificar:** `git diff` apontando alteração ou exclusão em arquivos `src/main/resources/db/migration/V*.sql` preexistentes na branch base.
3. **Consequências:** falha imediata na inicialização da aplicação (`FlywayValidateException: Migration checksum mismatch`), bloqueando o deploy.
4. **Alternativa recomendada:** manter o arquivo original intacto e criar uma nova migração incremental (`V<N+1>__*.sql`) com `ALTER TABLE`, `DROP INDEX` ou a correção necessária.
5. **Exceções:** migração recém-criada na mesma branch/PR que ainda não foi compartilhada nem aplicada em ambientes persistentes, mediante solicitação do usuário.
6. **Prioridade:** Crítica.

### MIG-02 Ponto flutuante ou tipo MONEY para valores financeiros

1. **Problema:** definir colunas de saldo, valor de lançamento, tarifa ou imposto como `REAL`, `FLOAT`, `DOUBLE PRECISION` ou o tipo legado `MONEY` do PostgreSQL.
2. **Como identificar:** presença de `FLOAT`, `REAL`, `DOUBLE PRECISION` ou `MONEY` em scripts `.sql`.
3. **Consequências:** erros de arredondamento binário em centavos (ponto flutuante) ou dependência da configuração regional `lc_monetary` do servidor (`MONEY`), causando corrupção de saldos financeiros (ver [COR-01 em JAVA_BEST_PRACTICES.md](./JAVA_BEST_PRACTICES.md#52-correção-e-persistência)).
4. **Alternativa recomendada:** usar sempre `NUMERIC(precisão, escala)` (ex.: `NUMERIC(19, 2)`) acompanhado de coluna `currency VARCHAR(3)`.
5. **Exceções:** nenhuma para dados financeiros.
6. **Prioridade:** Crítica.

### MIG-03 Chaves estrangeiras sem índice explícito

1. **Problema:** declarar `FOREIGN KEY (account_id) REFERENCES accounts (id)` assumindo que o PostgreSQL cria automaticamente um índice na coluna filha (`account_id`).
2. **Como identificar:** `CONSTRAINT fk_... FOREIGN KEY (col)` no script SQL sem um `CREATE INDEX` (ou `UNIQUE`) correspondente cuja primeira coluna seja `col`.
3. **Consequências:** o PostgreSQL cria índices automaticamente apenas para `PRIMARY KEY` e `UNIQUE`, **nunca** para `FOREIGN KEY`. Sem índice na FK, buscas por relacionamento (`JOIN`) e verificações na tabela pai realizam *Sequential Scan* na tabela filha inteira.
4. **Alternativa recomendada:** criar sempre um índice explícito para cada chave estrangeira (`CREATE INDEX idx_<table>_<fk_col> ON <table> (<fk_col>);` ou um índice composto iniciado pela coluna da FK).
5. **Exceções:** se já existir uma constraint `UNIQUE` ou outro índice B-Tree na tabela filha cuja primeira coluna seja exatamente a coluna da FK.
6. **Prioridade:** Alta.

### MIG-04 TIMESTAMP sem fuso horário para carimbos de tempo

1. **Problema:** usar `TIMESTAMP` (equivalente a `TIMESTAMP WITHOUT TIME ZONE`) para colunas como `created_at`, `updated_at`, `processed_at` ou `cancelled_at`.
2. **Como identificar:** declarações `TIMESTAMP NOT NULL` sem `WITH TIME ZONE` (ou sem o alias `TIMESTAMPTZ`) para eventos temporais.
3. **Consequências:** ambiguidade de horário quando o servidor da aplicação e o banco operam em fusos diferentes, quebrando a conversão direta para `java.time.Instant`.
4. **Alternativa recomendada:** usar sempre `TIMESTAMPTZ` para instantes no tempo e `DATE` para datas de calendário/competência sem hora (`LocalDate`).
5. **Exceções:** nenhuma para auditoria e eventos de domínio.
6. **Prioridade:** Alta.

### MIG-05 ON DELETE CASCADE em registros financeiros e constraints anônimas

1. **Problema:** configurar `ON DELETE CASCADE` em tabelas de lançamentos, transações ou faturas, ou declarar constraints inline sem nome (`PRIMARY KEY`, `REFERENCES`, `CHECK` anônimos).
2. **Como identificar:** `REFERENCES accounts(id) ON DELETE CASCADE` em tabelas contábeis; ausência de `CONSTRAINT <nome>` nas definições da tabela.
3. **Consequências:** `ON DELETE CASCADE` permite que a exclusão acidental de um registro pai apague silenciosamente todo o histórico contábil/financeiro; constraints sem nome recebem identificadores gerados pelo banco, dificultando o tratamento de erros de integridade e futuras migrações `ALTER TABLE ... DROP CONSTRAINT`.
4. **Alternativa recomendada:** usar `ON DELETE RESTRICT` em tabelas financeiras e nomear explicitamente todas as constraints (`pk_`, `fk_`, `uq_`, `ck_`).
5. **Exceções:** `ON DELETE CASCADE` é aceitável apenas em tabelas técnicas puramente dependentes sem valor contábil.
6. **Prioridade:** Alta.

### MIG-06 Coluna NOT NULL adicionada sem estratégia de transição

1. **Problema:** executar `ALTER TABLE <table> ADD COLUMN <col> <type> NOT NULL;` sem cláusula `DEFAULT` em uma tabela que já pode conter linhas.
2. **Como identificar:** `ADD COLUMN ... NOT NULL` sem `DEFAULT` em migrações posteriores à criação da tabela.
3. **Consequências:** a migração falha imediatamente em qualquer ambiente que já possua registros na tabela (`ERROR: column "..." of relation "..." contains null values`).
4. **Alternativa recomendada:** definir um `DEFAULT` válido na adição da coluna ou adicionar como anulável, atualizar os dados existentes (`UPDATE`) e então aplicar `SET NOT NULL`.
5. **Exceções:** quando a tabela acabou de ser criada na mesma migração e está comprovadamente vazia.
6. **Prioridade:** Alta.

### MIG-07 Uso de ENUM nativo do PostgreSQL ou ordinal numérico

1. **Problema:** criar tipos `CREATE TYPE ... AS ENUM (...)` no PostgreSQL ou usar `SMALLINT`/`INTEGER` para representar enums do Java (`EnumType.ORDINAL`).
2. **Como identificar:** `CREATE TYPE` em scripts Flyway ou colunas inteiras mapeadas para `enum` na entidade JPA sem `@Enumerated(EnumType.STRING)`.
3. **Consequências:** tipos `ENUM` nativos do PostgreSQL não permitem remover ou reordenar valores facilmente em migrações posteriores e exigem conversões específicas no JDBC/JPA; já `EnumType.ORDINAL` corrompe os dados existentes se a ordem das constantes no código Java for alterada.
4. **Alternativa recomendada:** usar `VARCHAR(n)` combinado com `@Enumerated(EnumType.STRING)` no Java e, quando apropriado, uma `CHECK` constraint nomeada (`CONSTRAINT ck_... CHECK (status IN (...))`) que pode ser facilmente substituída via `DROP CONSTRAINT` + `ADD CONSTRAINT`.
5. **Exceções:** nenhuma.
6. **Prioridade:** Média (Alta para `EnumType.ORDINAL`).

### MIG-08 Migração não validada contra PostgreSQL real (Testcontainers)

1. **Problema:** criar um script `.sql` ou alterar uma `@Entity` JPA / query jOOQ sem executar um teste de integração que aplique o Flyway no PostgreSQL via Testcontainers.
2. **Como identificar:** erros de sintaxe SQL, incompatibilidade de tipos entre a coluna da migração e a `@Entity` (`SchemaManagementException` do Hibernate) ou referências a tabelas/colunas inexistentes no jOOQ descobertos apenas ao subir a aplicação manualmente.
3. **Consequências:** quebra da inicialização do serviço e falha nos pipelines de integração contínua.
4. **Alternativa recomendada:** após criar ou editar qualquer migração, executar os testes que carregam [TestcontainersConfiguration.java](../src/test/java/com/emanudev/gestaofinanceira/TestcontainersConfiguration.java) (como `GestaoFinanceiraApplicationTests` ou testes `@DataJpaTest`/`@JooqTest`) via `./mvnw test`.
5. **Exceções:** ambientes sem Docker ativo, devendo a limitação ser explicitamente informada ao usuário na conclusão da tarefa.
6. **Prioridade:** Alta.

---

## 9. Referências

- [Flyway Documentation — Migrations & Naming](https://documentation.red-gate.com/fd/migrations-184127470.html)
- [PostgreSQL Documentation — Data Types (Numeric, Date/Time, UUID, Functions)](https://www.postgresql.org/docs/current/datatype.html)
- [PostgreSQL Documentation — Constraints, Foreign Keys & NOT VALID](https://www.postgresql.org/docs/current/ddl-constraints.html)
- [PostgreSQL Documentation — Partial Indexes](https://www.postgresql.org/docs/current/indexes-partial.html)
- [Spring Boot Reference — Flyway Database Migrations](https://docs.spring.io/spring-boot/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)
