# Especificação e Contratos dos Endpoints da API REST

Este documento descreve todos os endpoints REST da aplicação `gestao-financeira`. Ele reflete o contrato vigente depois das correções do teste funcional de novembro de 2026 (fases 1 a 6).

- **Base URL:** `/api` (definida globalmente em `server.servlet.context-path: /api`).
- **Versionamento:** prefixo `/v1` nas rotas dos controllers.
- **Formato de erro:** RFC 9457 `ProblemDetail` (`application/problem+json`), sempre com `errorCode`.
- **Autenticação:** JWT HS256 no header `Authorization: Bearer <token>`.
- **Perfis (RBAC):** `ADMIN`, `ANALISTA`, `AUDITOR`.

---

## Índice

0. [Convenções gerais](#0-convenções-gerais)
1. [Autenticação e Sessão (`/v1/auth`)](#1-autenticação-e-sessão-v1auth)
2. [Lançamentos Financeiros (`/v1/entries`)](#2-lançamentos-financeiros-v1entries)
3. [Comprovantes e Anexos Fiscais (`/v1/entries/{id}/attachments`)](#3-comprovantes-e-anexos-fiscais-v1entriesidattachments)
4. [Cadastros de Referência (`/v1/accounts`, `/v1/categories`, `/v1/counterparties`)](#4-cadastros-de-referência)
5. [Simulação de Risco / Monte Carlo HPC (`/v1/simulations`)](#5-simulação-de-risco--monte-carlo-hpc-v1simulations)
6. [Dashboard Executivo e Cone de Incerteza (`/v1/dashboard`)](#6-dashboard-executivo-e-cone-de-incerteza-v1dashboard)
7. [Exportação de Relatórios (`/v1/reports/entries/export`)](#7-exportação-de-relatórios)
8. [Auditoria e Rastreabilidade (`/v1/audit-logs`, `/v1/entries/{entryId}/audit-history`)](#8-auditoria-e-rastreabilidade)
9. [Infraestrutura (`/actuator`, `/v3/api-docs`)](#9-infraestrutura)

---

## 0. Convenções gerais

### 0.1 Códigos de status

| Situação | Status |
|---|---|
| Criação de recurso | `201 Created`, com o header `Location` apontando o recurso criado |
| Repetição idempotente de uma criação (mesma `Idempotency-Key` e mesmo conteúdo) | `200 OK`, com o mesmo `Location` |
| Remoção ou desativação | `204 No Content`, sem corpo. É idempotente: repetir também responde `204` |
| Corpo, parâmetro ou tipo inválido | `400 Bad Request`, com `fieldErrors` quando o problema é de campo |
| Token ausente, inválido, expirado ou revogado | `401 Unauthorized`, com o header `WWW-Authenticate: Bearer realm="gestao-financeira"` |
| Perfil sem permissão | `403 Forbidden` |
| Recurso inexistente ou rota inexistente | `404 Not Found` |
| Método não suportado | `405 Method Not Allowed` |
| Conflito de concorrência (versão desatualizada) ou de integridade de dados | `409 Conflict` |
| `If-Match` não corresponde à versão atual | `412 Precondition Failed` |
| `Content-Type` não suportado | `415 Unsupported Media Type` |
| Regra de negócio violada | `422 Unprocessable Entity` |
| Excesso de tentativas de login | `429 Too Many Requests`, com o header `Retry-After` |
| Erro inesperado | `500 Internal Server Error`, sem detalhes internos; usar o `traceId` para rastrear |
| Motor de simulação indisponível | `503 Service Unavailable` |

### 0.2 Formato de erro

```json
{
  "type": "http://localhost:8080/errors/missing-fiscal-attachment",
  "title": "Business Rule Violation",
  "status": 422,
  "detail": "Fiscal attachment is mandatory for payable entries of 5000.00 or more",
  "instance": "/api/v1/entries/0192c8a4-.../settle",
  "errorCode": "MISSING_FISCAL_ATTACHMENT",
  "error_code": "MISSING_FISCAL_ATTACHMENT",
  "timestamp": "2026-11-21T12:00:00Z",
  "traceId": "1343f5dbabb1850fcaf07cb8bd24ebaf",
  "fieldErrors": { "paidAmount": "must not be null" }
}
```

- O `detail` vem em inglês. O front traduz pelo `errorCode` (ver `MENSAGENS_ERRO` em `problem-detail.util.ts`).
- `fieldErrors` aparece apenas nos `400` de validação de campo.
- Campos desconhecidos no corpo JSON são rejeitados: `400 MALFORMED_REQUEST_BODY` (`fail-on-unknown-properties`).
- Datas com componente de hora em campos `LocalDate` também são rejeitadas, como `"2026-12-31T10:00:00"` (leniência desligada).

### 0.3 Concorrência otimista (`ETag` / `If-Match`)

- `GET /v1/entries/{id}` devolve o header `ETag: "<version>"`.
- Liquidação e cancelamento aceitam o header opcional `If-Match`. Se ele não corresponder à versão atual, a resposta é `412 PRECONDITION_FAILED`. Aceita `*`, `W/"n"` e listas.
- A liquidação continua exigindo `expectedVersion` no corpo; versão desatualizada dá `409 CONCURRENCY_CONFLICT`.

### 0.4 Paginação

- Parâmetros `page` (base 0) e `size` (de 1 a 500).
- Resposta `PagedResult<T>`:
  ```json
  { "items": [], "totalElements": 0, "page": 0, "size": 20, "totalPages": 0 }
  ```
- O offset é calculado em `long`: páginas muito altas devolvem lista vazia, não erro.

### 0.5 Valores monetários

- `Money` é serializado como `{ "amount": 1500.00, "currency": "BRL" }`, sem campos derivados.
- Os valores de entrada aceitam no máximo 2 casas decimais e 17 dígitos inteiros (`@Digits(17,2)`); fora disso dá `400`.
- **Teto por lançamento:** R$ 10.000.000,00, configurável em `APP_ENTRY_MAX_AMOUNT`. Acima disso dá `422 ENTRY_AMOUNT_ABOVE_LIMIT`.
- **Moeda:** dado novo precisa usar um código ISO-4217 válido (`400`) e a mesma moeda da conta (`422 CURRENCY_MISMATCH`). Registros legados com moeda fora da ISO continuam legíveis.

### 0.6 Busca textual

O parâmetro `search` faz busca sem diferenciar maiúsculas. `%` e `_` são tratados como texto literal.

### 0.7 CORS

As origens permitidas vêm de `APP_CORS_ALLOWED_ORIGINS`, separadas por vírgula; o padrão é `http://localhost:4200`. Os headers expostos ao navegador são `Location`, `ETag`, `Content-Disposition` e `Retry-After`.

---

## 1. Autenticação e Sessão (`/v1/auth`)

Controlador: `AuthControllerOpenApi` / `AuthController`

### Regras do token

- **Claims:** o access token traz `iss` (o emissor configurado), `sub` (id do usuário), `exp`, `jti`, `sid` (a sessão), `email`, `name` e `role`. Tokens sem `exp`, sem `jti` ou com outro emissor são recusados com `401`.
- **Usuário do token:** precisa existir e estar ativo a cada requisição. O papel efetivo vem do cadastro, não da claim `role`.
- **Segredo:** `APP_JWT_SECRET` precisa ter ao menos 32 bytes. Sem ele, a API só sobe com os perfis `dev` ou `test`, que usam o segredo embutido.
- **Validade:** o access token vale 15 minutos (`APP_JWT_ACCESS_TTL_SECONDS`), e o refresh token, 7 dias.

### 1.1 `POST /api/v1/auth/login`
- **Descrição:** autentica com e-mail e senha e devolve o access token e o refresh token.
- **Autorização:** pública.
- **Entrada:** `{ "email": "carlos.gestor@empresa.com", "password": "SenhaSegura@123" }`. O e-mail é aparado antes da validação.
- **Saída `200 OK`:**
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
    "tokenType": "Bearer",
    "expiresInSeconds": 900,
    "refreshToken": "uG81_xK9aPq...",
    "user": { "id": "0192c8a4-...", "fullName": "Carlos Gestor", "email": "carlos.gestor@empresa.com", "role": "ADMIN", "active": true }
  }
  ```
- **Erros:**
  - `400`: payload inválido ou JSON malformado (`MALFORMED_REQUEST_BODY`).
  - `401 INVALID_CREDENTIALS`: e-mail inexistente ou senha errada, inclusive para usuário inativo com senha errada. Um e-mail inexistente também passa pelo BCrypt, para o tempo de resposta não revelar se a conta existe.
  - `401 USER_INACTIVE`: só com a senha correta.
  - `429 TOO_MANY_LOGIN_ATTEMPTS`: depois de 5 falhas por conta ou 20 por IP, o login fica bloqueado por 15 minutos, com o header `Retry-After`. Os limites são configuráveis em `APP_LOGIN_MAX_FAILURES_PER_ACCOUNT`, `APP_LOGIN_MAX_FAILURES_PER_IP` e `APP_LOGIN_LOCK_MINUTES`.

### 1.2 `POST /api/v1/auth/refresh`
- **Descrição:** troca o refresh token por um novo par de tokens, na mesma sessão. A rotação é atômica: com pedidos paralelos usando o mesmo token, só um dá certo.
- **Autorização:** pública.
- **Entrada:** `{ "refreshToken": "uG81_xK9aPq..." }`.
- **Saída:** `200 OK`, no mesmo formato do login.
- **Erros:** `401 INVALID_REFRESH_TOKEN` para token inexistente, expirado ou já usado. Reutilizar um refresh token já rotacionado revoga a sessão inteira.

### 1.3 `POST /api/v1/auth/logout`
- **Descrição:** encerra só a sessão do access token atual. O access token deixa de valer na hora (lista de bloqueio no Redis até o `exp`), e os refresh tokens da sessão são revogados. Um `refreshToken` enviado no corpo só é revogado se pertencer ao usuário autenticado; caso contrário, é ignorado.
- **Autorização:** qualquer usuário autenticado.
- **Entrada (opcional):** `{ "refreshToken": "uG81_xK9aPq..." }`.
- **Saída:** `204 No Content`.

### 1.4 `GET /api/v1/auth/me`
- **Descrição:** dados do usuário autenticado. É a única rota de perfil; `/v1/users/me` foi removida.
- **Autorização:** qualquer usuário autenticado.
- **Saída `200 OK`:** `{ "id", "fullName", "email", "role", "active" }`.

---

## 2. Lançamentos Financeiros (`/v1/entries`)

Controlador: `FinancialEntryControllerOpenApi` / `FinancialEntryController`

Status possíveis: `PENDING`, `OVERDUE`, `PAID` (pagamento liquidado), `RECEIVED` (recebimento liquidado), `CANCELLED`. Não existe status "parcial": uma baixa parcial deixa o pai `PAID`/`RECEIVED` e cria um filho residual.

### 2.1 `POST /api/v1/entries`
- **Descrição:** cria um lançamento a pagar ou a receber. Ele nasce `PENDING`, ou `OVERDUE` se o vencimento já passou.
- **Autorização:** `ADMIN` ou `ANALISTA`.
- **Header opcional:** `Idempotency-Key`, com até 100 caracteres. Também é aceito no corpo, como `idempotencyKey`.
- **Entrada:**
  ```json
  {
    "description": "Licença Cloud AWS",
    "entryType": "PAYABLE",
    "originalAmount": 7500.00,
    "currency": "BRL",
    "dueDate": "2026-11-15",
    "competenceDate": "2026-10-01",
    "categoryId": "0192c8a4-0000-7000-8000-000000000020",
    "accountId": "0192c8a4-0000-7000-8000-000000000010",
    "counterpartyId": "0192c8a4-0000-7000-8000-000000000030"
  }
  ```
  - `accountId` e `counterpartyId` são opcionais. Sem conta, usa a primeira conta ativa; sem `competenceDate`, usa o `dueDate`.
  - A categoria precisa aceitar o `entryType`. Uma categoria `BOTH` serve aos dois tipos.
- **Saídas:**
  - `201 Created`: devolve `FinancialEntryResponse`, com os headers `Location` e `ETag`.
  - `200 OK`: repetição com a mesma `Idempotency-Key` e o mesmo conteúdo. Devolve o lançamento original e não grava nova auditoria.
- **Erros:**
  - `400`: campo obrigatório ausente; valor ausente, zero ou negativo; mais de 2 casas decimais; `dueDate` anterior à `competenceDate` (`fieldErrors.dueDateOnOrAfterCompetenceDate`); moeda fora da ISO; `Idempotency-Key` com mais de 100 caracteres.
  - `403`: perfil `AUDITOR`.
  - `422`: `ACCOUNT_NOT_FOUND`, `CATEGORY_NOT_FOUND`, `COUNTERPARTY_NOT_FOUND`, `INACTIVE_ACCOUNT`, `INACTIVE_CATEGORY`, `INACTIVE_COUNTERPARTY`, `INCOMPATIBLE_CATEGORY_TYPE`, `ENTRY_AMOUNT_ABOVE_LIMIT`, `CURRENCY_MISMATCH`, `IDEMPOTENCY_KEY_REUSED` (mesma chave com conteúdo diferente).

### 2.2 `GET /api/v1/entries`
- **Descrição:** lista paginada e filtrada.
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Parâmetros:**
  - `entryType` (`PAYABLE` ou `RECEIVABLE`) e `status` (`PENDING`, `OVERDUE`, `PAID`, `RECEIVED` ou `CANCELLED`);
  - `categoryId` e `accountId`;
  - `dueDateFrom` e `dueDateTo` (`YYYY-MM-DD`);
  - `search`, buscando em descrição, contraparte e categoria;
  - `page` (padrão 0) e `size` (padrão 20, máximo 500).
- **Saída:** `200 OK`, com `PagedResult<FinancialEntrySummaryView>`. Cada item traz `outstandingAmount`: o valor original se estiver em aberto, `0` se estiver liquidado ou cancelado.
- **Erros:** `400` para enum ou UUID inválido.

### 2.3 `GET /api/v1/entries/{id}`
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Saída `200 OK`:** devolve `FinancialEntryResponse`, com o header `ETag: "<version>"`. Campos:
  - identificação e vínculos: `id`, `idempotencyKey`, `parentEntryId`, `rootEntryId`, `accountId`/`accountName`, `categoryId`/`categoryName`/`categoryRequiresAttachment`, `counterpartyId`/`counterpartyName`;
  - dados do lançamento: `entryType`, `status`, `description`, `competenceDate`, `dueDate`, `settlementDate`;
  - valores: `originalAmount`, `settledAmount`, `outstandingAmount`, `discountAmount`, `interestAmount`, `lateFeeAmount`, `currency`;
  - controle: `attachmentCount`, `cancellationReason`, `cancelledAt`, `cancelledBy`, `createdBy`, `version`, `createdAt`, `updatedAt`.
- **Erros:** `400` para id que não é UUID e `404` para lançamento inexistente.

### 2.4 `POST /api/v1/entries/{id}/settle`
- **Descrição:** liquidação total ou parcial de um lançamento `PENDING` ou `OVERDUE`.
- **Autorização:** `ADMIN` ou `ANALISTA`.
- **Header opcional:** `If-Match` (ver 0.3).
- **Entrada:**
  ```json
  {
    "paidAmount": 400.00,
    "discountAmount": 0.00,
    "interestAmount": 0.00,
    "lateFeeAmount": 0.00,
    "settlementDate": "2026-11-21",
    "allowPartial": true,
    "expectedVersion": 3
  }
  ```
  - `paidAmount` e `expectedVersion` são obrigatórios.
  - `allowPartial` vale `false` quando omitido.
  - `settlementDate` vale hoje quando omitida, não pode ser futura e pode ser anterior à competência (pagamento antecipado).
- **Regras:**
  - Se o valor pago for menor que o saldo e `allowPartial=true`, o lançamento pai fica `PAID`/`RECEIVED` com o valor pago, e nasce um filho residual `PENDING` com a diferença. A auditoria registra `PARTIAL_SETTLE_SPLIT`.
  - Se o valor pago for menor que o saldo e `allowPartial=false`, a resposta é `422 UNDERPAYMENT_REQUIRES_SPLIT`.
  - Lançamentos a pagar a partir de R$ 5.000,00, ou de categoria com anexo obrigatório, exigem anexo fiscal antes (`422 MISSING_FISCAL_ATTACHMENT`).
- **Saída `200 OK`:** devolve `SettlementResponse`. Campos:
  - identificação: `id`, `parentEntryId`, `rootEntryId`;
  - dados do lançamento: `entryType`, `status`, `description`, `dueDate`, `settlementDate`;
  - valores: `originalAmount`, `settledAmount`, `discountAmount`, `interestAmount`, `lateFeeAmount`, `currency`;
  - controle: `version`;
  - baixa parcial: `hasResidualChild`, `residualEntryId`, `settledEntry`, `residualChildEntry`.
- **Erros:**
  - `400`: corpo ausente ou malformado, sem `paidAmount` ou `expectedVersion`, ou com campo desconhecido.
  - `404`: lançamento inexistente.
  - `409 CONCURRENCY_CONFLICT`: versão desatualizada.
  - `412 PRECONDITION_FAILED`: `If-Match` divergente.
  - `422`: `MISSING_FISCAL_ATTACHMENT`, `UNDERPAYMENT_REQUIRES_SPLIT`, `INVALID_SETTLEMENT_DATE`, transição de status inválida.

### 2.5 `DELETE /api/v1/entries/{id}?reason=<motivo>`
- **Descrição:** cancelamento lógico (soft delete), que é a única forma de cancelar; o antigo `POST /{id}/cancel` foi removido. O motivo vai na query string, porque a operação não aceita corpo.
- **Autorização:** `ADMIN`.
- **Header opcional:** `If-Match`.
- **Saída:** `204 No Content`. É idempotente: repetir num lançamento já cancelado responde `204` e não grava nova auditoria.
- **Erros:**
  - `400`: motivo com mais de 500 caracteres.
  - `403`: perfil sem permissão.
  - `404`: lançamento inexistente.
  - `412`: `If-Match` divergente.
  - `422`: `INVALID_CANCELLATION_REASON` (menos de 5 caracteres depois do trim); `ENTRY_HAS_ACTIVE_CHILDREN` (pai de baixa parcial com residual ativo; cancele o residual antes); `CANNOT_CANCEL_SETTLED_ENTRY`.

### 2.6 `GET /api/v1/entries/{id}/splits`
- **Descrição:** árvore de desdobramentos, com o lançamento raiz e os residuais das baixas parciais. Cada nó traz `outstandingAmount`.
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Saídas:** `200 OK`, com `List<FinancialEntryLineageNode>`, ou `404`.

---

## 3. Comprovantes e Anexos Fiscais (`/v1/entries/{id}/attachments`)

Controlador: `AttachmentControllerOpenApi` / `AttachmentController`

### 3.1 `POST /api/v1/entries/{id}/attachments`
- **Descrição:** envia um comprovante para o S3 (MiniStack em ambiente local) e o vincula ao lançamento.
- **Autorização:** `ADMIN` ou `ANALISTA`.
- **Entrada (`multipart/form-data`):** campo `file`, com PDF, JPEG ou PNG de até 10 MB (`APP_ATTACHMENT_MAX_FILE_SIZE`). O tipo é validado pela assinatura binária do arquivo. O nome original mantém acentos e pontos seguidos (`nota..fiscal.pdf`); só separadores de caminho são descartados.
- **Saída `201 Created`:** devolve `FiscalAttachmentResponse`, com o header `Location` apontando o download. Campos: `id`, `entryId`, `originalFileName`, `contentType`, `fileSizeBytes`, `sha256Checksum`, `uploadedBy`, `uploadedAt`.
- **Erros:**
  - `400 MALFORMED_MULTIPART_REQUEST`: requisição multipart malformada.
  - `404`: lançamento inexistente.
  - `422`: `ATTACHMENT_TOO_LARGE` (acima de 10 MB); `ENTRY_CANCELLED` (lançamento cancelado); arquivo vazio; formato não aceito.

### 3.2 `GET /api/v1/entries/{id}/attachments`
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Saídas:** `200 OK`, com `List<FiscalAttachmentResponse>`, ou `404`.

### 3.3 `GET /api/v1/entries/{id}/attachments/{attachmentId}/download`
- **Descrição:** download em streaming, sem carregar o arquivo inteiro em memória.
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Saída `200 OK`:** o conteúdo binário, com `Content-Length` e `Content-Disposition: attachment; filename*=UTF-8''<nome>`.
- **Erros:** `404`.

---

## 4. Cadastros de Referência

Controladores: `FinancialAccountController`, `CategoryController`, `CounterpartyController`

Regras comuns aos três cadastros:
- **Listas:**
  - são paginadas (`page`, `size` até 500, padrão 50) e ordenadas por nome;
  - por padrão trazem só os ativos, e `includeInactive=true` inclui os desativados.
- **Criação:** responde `201` com `Location`.
- **`PATCH /{id}`:** altera só os campos enviados; `{"active": true}` reativa. Responde `200`, `404` ou `422` (nome duplicado).
- **`DELETE /{id}`:** desativa (exclusão lógica) e responde `204`. É idempotente.
- **Leitura:** `GET` liberado para `ADMIN`, `ANALISTA` e `AUDITOR`.

### 4.1 Contas Financeiras (`/v1/accounts`)
O alias `/v1/financial-accounts` foi removido.
- **Mutações (`POST`, `PATCH`, `DELETE`):** só `ADMIN`.
- **`POST`:**
  - entrada `{"name": "Conta Corrente Itaú", "accountType": "OPERATIONAL_LEDGER", "initialBalance": 25000.00, "currency": "BRL"}`;
  - `accountType` aceita `OPERATIONAL_LEDGER`, `TREASURY_LEDGER` e `PETTY_CASH`;
  - erros: `400` (nome com mais de 120 caracteres, moeda inválida, saldo fora do formato) e `422 DUPLICATE_ACCOUNT_NAME` (o nome é comparado sem diferenciar maiúsculas).
- **`PATCH`:** aceita `name` e `active`.
- **Resposta:** `{ "id", "name", "accountType", "initialBalance", "currentBalance", "currency", "active", "createdAt" }`. O `currentBalance` é o saldo inicial mais o líquido das liquidações da conta até hoje.

### 4.2 Categorias (`/v1/categories`)
- **Mutações:** só `ADMIN`.
- **`POST`:**
  - entrada `{"name": "Serviços em Nuvem", "categoryType": "PAYABLE", "requiresAttachmentAlways": false, "fixedCost": true}`;
  - `categoryType` aceita `PAYABLE`, `RECEIVABLE` e `BOTH`;
  - o nome tem até 100 caracteres;
  - nome duplicado no mesmo tipo dá `422 DUPLICATE_CATEGORY`/`DUPLICATE_CATEGORY_NAME`.
- **`PATCH`:** aceita `name`, `requiresAttachmentAlways`, `fixedCost` e `active`.
- **Resposta:** `{ "id", "name", "categoryType", "requiresAttachmentAlways", "fixedCost", "active", "createdAt" }`.

### 4.3 Contrapartes (`/v1/counterparties`)
- **Mutações:** `ADMIN` e `ANALISTA`.
- **`POST`:**
  - entrada `{"legalName": "Amazon Web Services Inc", "tradeName": "AWS", "taxId": "12.345.678/0001-95", "counterpartyType": "SUPPLIER"}`;
  - `counterpartyType` aceita `CUSTOMER`, `SUPPLIER`, `GOVERNMENT`, `EMPLOYEE` e `OTHER`;
  - `legalName` e `tradeName` têm até 180 caracteres.
  - **CPF/CNPJ (`taxId`):**
    - é opcional; quando informado, tem o dígito verificador validado (`422 INVALID_TAX_ID`) e é gravado só com dígitos;
    - o mesmo documento com ou sem máscara conta como duplicado (`422 DUPLICATE_COUNTERPARTY_TAX_ID`).
- **`PATCH`:** aceita `legalName`, `tradeName` e `active`. O documento não pode ser alterado.
- **Resposta:** `{ "id", "legalName", "tradeName", "taxId", "counterpartyType", "active", "createdAt" }`.

---

## 5. Simulação de Risco / Monte Carlo HPC (`/v1/simulations`)

Controlador: `SimulationControllerOpenApi` / `SimulationController`

### 5.1 `POST /api/v1/simulations`
- **Descrição:** executa a simulação de Monte Carlo, com 100.000 a 1.000.000 de cenários. Roda no motor C++20 via gRPC ou, como alternativa, na JVM. O saldo inicial usa a mesma regra do saldo consolidado (ver 6.1).
- **Autorização:** `ADMIN`.
- **Entrada (opcional):**
  ```json
  { "horizonDays": 60, "numScenarios": 200000, "expectedDefaultRate": 8.0, "variableCostShockPct": 15.0, "randomSeed": "8361509756792018453" }
  ```
  - `randomSeed` aceita número ou string e precisa ser ≥ 1. Envie como string para valores acima de 2^53.
  - Os percentuais vão de 0 a 100.
- **Saída `201 Created`:** devolve `SimulationResponse`, com `Location: /api/v1/simulations/{id}`. O `randomSeed` sai como **string**.
  ```json
  {
    "id": "0192c8a4-...", "horizonDays": 60, "numScenarios": 200000, "initialBalance": 155817.18,
    "expectedDefaultRate": 8.0, "variableCostShockPct": 15.0, "randomSeed": "8361509756792018453",
    "var99": -14250.80, "medianP50": 68420.00, "ruinProbabilityPct": 4.25,
    "dailyBands": [{ "dayOffset": 1, "date": "2026-11-22", "p01": 0, "p05": 0, "p25": 0, "p50": 0, "p75": 0, "p95": 0 }],
    "executionDevice": "JVM-Fallback-ParallelStream", "executionTimeMs": 85, "createdAt": "2026-11-21T12:00:00Z"
  }
  ```
- **Erros:**
  - `400 INVALID_ARGUMENT`: percentual fora de 0 a 100.
  - `403`: perfil sem permissão.
  - `422`: `INVALID_SIMULATION_HORIZON` (diferente de 30, 60 ou 90); `INSUFFICIENT_SIMULATION_SCENARIOS`/`EXCESSIVE_SIMULATION_SCENARIOS`; `INVALID_RANDOM_SEED`; `SIMULATION_AMOUNT_OUT_OF_RANGE` (valores grandes demais para o cálculo em centavos).
  - `503 RISK_ENGINE_UNAVAILABLE`: motor indisponível ou limite de simulações simultâneas esgotado.

### 5.2 `GET /api/v1/simulations/latest`
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Saídas:** `200 OK` ou `404` (nenhuma simulação executada).

### 5.3 `GET /api/v1/simulations/{id}`
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Saídas:** `200 OK` ou `404`.

---

## 6. Dashboard Executivo e Cone de Incerteza (`/v1/dashboard`)

Controlador: `DashboardControllerOpenApi` / `DashboardController`

### 6.1 `GET /api/v1/dashboard/kpis`
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Parâmetros:** `referenceDate` (opcional, padrão hoje).
- **Regras:**
  - **Saldo consolidado:** soma os saldos iniciais das contas ativas mais o líquido das liquidações dessas contas com data até `referenceDate`. É a mesma regra do último ponto do histórico do cone e do saldo inicial da simulação, então os três valores batem.
  - **A receber/pagar hoje:** só os lançamentos em aberto com vencimento igual a `referenceDate`.
  - **Vencidos:** os lançamentos em aberto com vencimento anterior a `referenceDate`.
- **Saída `200 OK`:**
  ```json
  {
    "consolidatedBalance": { "amount": 154320.50, "currency": "BRL" },
    "totalReceivableToday": { "amount": 18200.00, "currency": "BRL" },
    "totalPayableToday": { "amount": 9450.75, "currency": "BRL" },
    "totalReceivableOverdue": { "amount": 321.09, "currency": "BRL" },
    "totalPayableOverdue": { "amount": 4321.81, "currency": "BRL" },
    "monthlyDelinquencyRatePct": { "value": 4.25 },
    "referenceDate": "2026-11-21",
    "generatedAt": "2026-11-21T12:00:00Z"
  }
  ```

### 6.2 `GET /api/v1/dashboard/uncertainty-cone`
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Parâmetros:**
  - `historyDays` (padrão 30, máximo 365);
  - `simulationId` (opcional; sem ele, usa a simulação mais recente);
  - `referenceDate` (opcional).
- **Saída `200 OK`:**
  ```json
  {
    "historicalSeries": [{ "date": "2026-11-20", "balance": { "amount": 150000.00, "currency": "BRL" } }],
    "projectionCone": {
      "simulationId": "0192c8a4-...", "horizonDays": 30, "numScenarios": 100000,
      "var99": { "amount": 68763.37, "currency": "BRL" }, "medianP50": { "amount": 71869.97, "currency": "BRL" },
      "ruinProbabilityPct": { "value": 0.0 }, "executionDevice": "JVM-Fallback-ParallelStream", "createdAt": "2026-11-19T01:47:13Z",
      "dailyBands": [{ "dayOffset": 1, "date": "2026-11-22", "p01": 0, "p05": 0, "p25": 0, "p50": 0, "p75": 0, "p95": 0 }]
    }
  }
  ```
  - `projectionCone` é `null` quando não há nenhuma simulação.
- **Erros:** `404` quando o `simulationId` informado não existe.

---

## 7. Exportação de Relatórios

Controlador: `ReportExportControllerOpenApi` / `ReportExportController`

### 7.1 `GET /api/v1/reports/entries/export`
- **Descrição:** é a única rota de exportação. Foram removidas `POST /v1/reports/export`, `GET /v1/entries/export/csv` e `GET /v1/entries/export/pdf`.
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Parâmetros:** `format` (`CSV` ou `PDF`, padrão `CSV`) e os mesmos filtros da lista de lançamentos (`entryType`, `status`, `categoryId`, `accountId`, `dueDateFrom`, `dueDateTo`, `search`).
- **Saída `200 OK`:** o arquivo, com `Content-Disposition: attachment; filename="relatorio-financeiro-AAAA-MM-DD.csv|pdf"`.
- **Formatos:**
  - **CSV:** UTF-8 com BOM, separador `;`, gerado em streaming.
  - **PDF:** várias páginas, com "Página x de y" e o hash SHA-256 de integridade na última página.
    - Acentos do português aparecem (WinAnsi); caracteres fora do Latin-1 saem como `?`.
    - Nos totalizadores, uma baixa parcial conta uma vez só (valor liquidado do pai mais o residual em aberto), e os cancelados não entram.
- **Erros:** `400 INVALID_ARGUMENT` para formato diferente de `CSV` ou `PDF`.

---

## 8. Auditoria e Rastreabilidade

Controlador: `AuditLogControllerOpenApi` / `AuditLogController`

A tabela `audit_logs` só aceita inclusões: triggers bloqueiam `UPDATE`, `DELETE` e `TRUNCATE` (migração V8).

### 8.1 `GET /api/v1/audit-logs`
- **Autorização:** `ADMIN` ou `AUDITOR`; `ANALISTA` recebe `403`.
- **Parâmetros:**
  - `entityType` (`FINANCIAL_ENTRY`, `FINANCIAL_ACCOUNT`, `CATEGORY` ou `COUNTERPARTY`);
  - `entityId` e `actorUserId`;
  - `action` (`CREATE`, `UPDATE`, `SETTLE`, `PARTIAL_SETTLE_SPLIT`, `CANCEL_SOFT_DELETE`, `AUTO_OVERDUE_TRANSITION` ou `UPLOAD_ATTACHMENT`);
  - `startDate` e `endDate` (ISO 8601 UTC);
  - `page` e `size`.
- **Saída `200 OK`:** devolve `PagedResult<AuditLogItemResponse>`. Campos:
  - identificação: `id`, `entityType`, `entityId`, `action`, `correlationId`, `occurredAt`;
  - autor: `actorUserId`, `actorName`, `actorRole`, `actorType`, `ipAddress`. O IP é o da conexão; `X-Forwarded-For` só vale atrás de proxy configurado em `SERVER_FORWARD_HEADERS_STRATEGY`.
  - estado: `previousState` e `newState`, como **objetos JSON**.
    - Na baixa parcial, `newState` vem como `{ "settledEntry": {...}, "residualChildEntry": {...} }`.
    - Alterações de cadastro (`PATCH` e desativação) são registradas como `UPDATE`.

### 8.2 `GET /api/v1/entries/{entryId}/audit-history`
- **Descrição:** linha do tempo do lançamento, em ordem cronológica crescente (a primeira linha é o `CREATE`).
- **Autorização:** `ADMIN` ou `AUDITOR`.
- **Saídas:** `200 OK`, com `List<AuditLogItemResponse>`, ou `404` para lançamento inexistente.

---

## 9. Infraestrutura

| Rota | Acesso |
|---|---|
| `GET /api/actuator/health`, `/api/actuator/info` | Pública |
| `GET /api/actuator/prometheus` | `ADMIN` |
| `/api/v3/api-docs/**`, `/api/swagger-ui/**` | Pública |
| `/api/error` | Pública, para que erros de despacho não virem `401` |

O log da aplicação é estruturado em JSON ECS (`logging.structured.format.console: ecs`), com `traceId` e correlation id.
