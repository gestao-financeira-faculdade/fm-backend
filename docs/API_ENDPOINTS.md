# Especificação e Contratos dos Endpoints da API REST

Documentação operacional de todos os endpoints REST da aplicação `gestao-financeira`.

- **Base URL:** `/api` (configurado globalmente via `server.servlet.context-path: /api`).
- **Padrão de Versionamento:** `/v1` nas rotas dos controllers.
- **Formato de Erro Padrão:** RFC 9457 `ProblemDetail` (`application/problem+json`).
- **Autenticação:** Bearer Token JWT (HS256) via header `Authorization: Bearer <token>`.
- **RBAC Roles:** `ADMIN`, `ANALISTA`, `AUDITOR`.

---

## Índice

1. [Autenticação e Sessão (`/v1/auth`)](#1-autenticação-e-sessão-v1auth)
2. [Lançamentos Financeiros (`/v1/entries`)](#2-lançamentos-financeiros-v1entries)
3. [Comprovantes e Anexos Fiscais (`/v1/entries/{id}/attachments`)](#3-comprovantes-e-anexos-fiscais-v1entriesidattachments)
4. [Dados Mestres / Cadastros de Referência (`/v1/accounts`, `/v1/categories`, `/v1/counterparties`)](#4-dados-mestres--cadastros-de-referência)
5. [Simulação Estocástica de Risco / Monte Carlo HPC (`/v1/simulations`)](#5-simulação-estocástica-de-risco--monte-carlo-hpc-v1simulations)
6. [Dashboard Executivo e Cone de Incerteza (`/v1/dashboard`)](#6-dashboard-executivo-e-cone-de-incerteza-v1dashboard)
7. [Exportação de Relatórios Financeiros (`/v1/reports`, `/v1/entries/export`)](#7-exportação-de-relatórios-financeiros)
8. [Auditoria e Rastreabilidade (`/v1/audit-logs`, `/v1/entries/{entryId}/audit-history`)](#8-auditoria-e-rastreabilidade)

---

## 1. Autenticação e Sessão (`/v1/auth`)

Controlador: `AuthControllerOpenApi` / `AuthController`

### 1.1 `POST /api/v1/auth/login`
- **Descrição:** Autentica credenciais de usuário (email e senha), emitindo Access Token JWT e Refresh Token opaco.
- **Autorização:** Pública (sem necessidade de token prévio).
- **Entrada (Request Body - `application/json`):**
  ```json
  {
    "email": "carlos.gestor@empresa.com",
    "password": "SenhaSegura@123"
  }
  ```
- **Saídas e Códigos de Status:**
  - **`200 OK`:** Login realizado com sucesso.
    ```json
    {
      "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
      "tokenType": "Bearer",
      "expiresInSeconds": 900,
      "refreshToken": "uG81_xK9aPq...",
      "user": {
        "id": "0192c8a4-0000-7000-8000-000000000001",
        "fullName": "Carlos Gestor",
        "email": "carlos.gestor@empresa.com",
        "role": "ADMIN",
        "active": true
      }
    }
    ```
  - **`400 Bad Request`:** Payload inválido ou campos obrigatórios ausentes.
  - **`401 Unauthorized`:** Credenciais incorretas ou usuário inativo.

### 1.2 `POST /api/v1/auth/refresh`
- **Descrição:** Renova o Access Token JWT expirado utilizando um Refresh Token ativo armazenado no Redis.
- **Autorização:** Pública (mediante apresentação de Refresh Token válido).
- **Entrada (Request Body - `application/json`):**
  ```json
  {
    "refreshToken": "uG81_xK9aPq..."
  }
  ```
- **Saídas e Códigos de Status:**
  - **`200 OK`:** Token renovado com sucesso (retorna novo Access Token, novo Refresh Token rotacionado e TTL).
  - **`401 Unauthorized`:** Refresh Token expirado, revogado ou inválido.

### 1.3 `POST /api/v1/auth/logout`
- **Descrição:** Revoga o Refresh Token ativo da sessão no Redis e invalida a autenticação corrente.
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR` (`@PreAuthorize("isAuthenticated()")`).
- **Entrada (Request Body - `application/json`):**
  ```json
  {
    "refreshToken": "uG81_xK9aPq..."
  }
  ```
- **Saídas e Códigos de Status:**
  - **`204 No Content`:** Logout concluído com sucesso.
  - **`401 Unauthorized`:** Usuário não autenticado.

### 1.4 `GET /api/v1/auth/me`
- **Descrição:** Retorna os dados cadastrais e o perfil de permissões (Role) do usuário autenticado.
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Saídas e Códigos de Status:**
  - **`200 OK`:** Retorna dados de `UserSummaryResponse`.
  - **`401 Unauthorized`:** Token ausente ou expirado.

---

## 2. Lançamentos Financeiros (`/v1/entries`)

Controlador: `FinancialEntryControllerOpenApi` / `FinancialEntryController`

### 2.1 `POST /api/v1/entries`
- **Descrição:** Cria um novo lançamento financeiro (conta a pagar ou a receber), gerando invariantes e idempotência.
- **Autorização:** `ADMIN` ou `ANALISTA`.
- **Headers Opcionais:** `Idempotency-Key: <UUID ou Hash>` (evita duplicação em retentativas).
- **Entrada (Request Body - `application/json`):**
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
- **Saídas e Códigos de Status:**
  - **`201 Created`:** Lançamento criado com status `PENDING`. Retorna `FinancialEntryResponse`.
  - **`400 Bad Request`:** Erro sintático de validação (`dueDate` anterior à `competenceDate`, valor negativo, etc.).
  - **`403 Forbidden`:** Perfil `AUDITOR` tentou criar lançamento.
  - **`422 Unprocessable Entity`:** Violação semântica de negócio (ex.: conta inativa, categoria inexistente).

### 2.2 `GET /api/v1/entries`
- **Descrição:** Consulta paginada e filtrada de lançamentos financeiros via projeções jOOQ otimizadas.
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Parâmetros de Consulta (Query Params):**
  - `entryType` (opcional): `PAYABLE` ou `RECEIVABLE`.
  - `status` (opcional): `PENDING`, `SETTLED`, `PARTIALLY_SETTLED`, `OVERDUE`, `CANCELLED`.
  - `categoryId`, `accountId` (opcionais, UUID).
  - `dueDateFrom`, `dueDateTo` (opcionais, formato ISO `YYYY-MM-DD`).
  - `search` (opcional): busca textual na descrição.
  - `page` (default `0`), `size` (default `20`).
- **Saídas e Códigos de Status:**
  - **`200 OK`:** `PagedResult<FinancialEntrySummaryView>` contendo itens, total de elementos, página e tamanho.

### 2.3 `GET /api/v1/entries/{id}`
- **Descrição:** Busca os detalhes completos de um lançamento financeiro por UUID.
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Saídas e Códigos de Status:**
  - **`200 OK`:** Retorna `FinancialEntryResponse`.
  - **`404 Not Found`:** Lançamento não encontrado com o ID especificado.

### 2.4 `POST /api/v1/entries/{id}/settle`
- **Descrição:** Realiza a liquidação (baixa) total ou parcial de um lançamento financeiro.
- **Autorização:** `ADMIN` ou `ANALISTA`.
- **Regras de Negócio Críticas:**
  - Se valor pago for menor que o saldo devedor, realiza **baixa parcial com split** (`PARTIAL_SETTLE_SPLIT`), gerando automaticamente um lançamento filho com o saldo residual.
  - Lançamentos com valor total >= R$ 5.000,00 ou categorias que exijam comprovante obrigatório barram a liquidação se não houver anexo fiscal prévio (`MISSING_FISCAL_ATTACHMENT`).
- **Entrada (Request Body - `application/json`):**
  ```json
  {
    "paidAmount": 5000.00,
    "discountAmount": 0.00,
    "interestAmount": 50.00,
    "lateFeeAmount": 20.00,
    "settlementDate": "2026-10-26",
    "allowPartial": true,
    "expectedVersion": 0
  }
  ```
- **Saídas e Códigos de Status:**
  - **`200 OK`:** Baixa realizada com sucesso. Retorna `SettlementResponse` com status (`SETTLED` ou `PARTIALLY_SETTLED`) e ID do lançamento residual quando gerado.
  - **`404 Not Found`:** Lançamento não encontrado.
  - **`409 Conflict`:** Conflito de concorrência (`OptimisticLockException` por alteração simultânea).
  - **`422 Unprocessable Entity`:** Falta de anexo fiscal obrigatório (`MISSING_FISCAL_ATTACHMENT`) ou lançamento já liquidado/cancelado.

### 2.5 `POST /api/v1/entries/{id}/cancel` e `DELETE /api/v1/entries/{id}`
- **Descrição:** Cancela ou exclui logicamente (*Soft Delete*) um lançamento pendente.
- **Autorização:** Exclusivo para perfil `ADMIN`.
- **Entrada (Body ou Query Param):** `reason` (motivo do cancelamento).
- **Saídas e Códigos de Status:**
  - **`200 OK`:** Lançamento marcado com status `CANCELLED`.
  - **`403 Forbidden`:** Perfil `ANALISTA` ou `AUDITOR` tentou cancelar/excluir.
  - **`422 Unprocessable Entity`:** Lançamento já liquidado ou com filhos ativos no desdobramento.

### 2.6 `GET /api/v1/entries/{id}/splits`
- **Descrição:** Retorna a árvore hierárquica completa de desdobramentos (lançamento raiz, baixas parciais e resíduos derivados).
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Saídas e Códigos de Status:**
  - **`200 OK`:** Retorna `List<FinancialEntryLineageNode>`.
  - **`404 Not Found`:** Lançamento não localizado.

---

## 3. Comprovantes e Anexos Fiscais (`/v1/entries/{id}/attachments`)

Controlador: `AttachmentControllerOpenApi` / `AttachmentController`

### 3.1 `POST /api/v1/entries/{id}/attachments`
- **Descrição:** Faz upload de comprovante/anexo fiscal no bucket AWS S3 (ou MiniStack local) e vincula ao lançamento.
- **Autorização:** `ADMIN` ou `ANALISTA`.
- **Entrada (`multipart/form-data`):**
  - Campo `file`: arquivo binário (`PDF`, `JPEG` ou `PNG`, máx. 10 MB). Valida *magic numbers* de assinatura binária no domínio.
- **Saídas e Códigos de Status:**
  - **`201 Created`:** Anexo salvo e registrado. Retorna `FiscalAttachmentResponse`.
  - **`404 Not Found`:** Lançamento pai não existe.
  - **`422 Unprocessable Entity`:** Arquivo vazio, tamanho excessivo (> 10MB) ou extensão/assinatura incompatível.

### 3.2 `GET /api/v1/entries/{id}/attachments`
- **Descrição:** Lista os metadados de todos os anexos fiscais vinculados ao lançamento.
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Saídas e Códigos de Status:**
  - **`200 OK`:** Retorna `List<FiscalAttachmentResponse>`.
  - **`404 Not Found`:** Lançamento não encontrado.

### 3.3 `GET /api/v1/entries/{id}/attachments/{attachmentId}/download`
- **Descrição:** Realiza o download do arquivo binário armazenado no S3.
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Saídas e Códigos de Status:**
  - **`200 OK`:** Retorna stream binário (`application/pdf`, `image/jpeg`, `image/png`) com header `Content-Disposition`.
  - **`404 Not Found`:** Anexo ou lançamento inexistente.

---

## 4. Dados Mestres / Cadastros de Referência

Controladores: `FinancialAccountController`, `CategoryController`, `CounterpartyController`

### 4.1 Contas Financeiras (`/v1/accounts`, `/v1/financial-accounts`)
- **`GET /api/v1/accounts`:** Lista todas as contas ativas (`200 OK`). Roles: `ADMIN`, `ANALISTA`, `AUDITOR`.
- **`GET /api/v1/accounts/{id}`:** Busca detalhes da conta por ID (`200 OK`, `404 Not Found`).
- **`POST /api/v1/accounts`:** Cria nova conta financeira. Role: `ADMIN`.
  - **Entrada:** `{"name": "Conta Corrente Itaú", "accountType": "OPERATIONAL_LEDGER", "initialBalance": 25000.00, "currency": "BRL"}`
  - **Status:** `201 Created`, `400 Bad Request`, `403 Forbidden`, `422 Unprocessable Entity` (nome duplicado).

### 4.2 Categorias Financeiras (`/v1/categories`)
- **`GET /api/v1/categories`:** Lista todas as categorias ativas (`200 OK`). Roles: `ADMIN`, `ANALISTA`, `AUDITOR`.
- **`GET /api/v1/categories/{id}`:** Busca categoria por ID (`200 OK`, `404 Not Found`).
- **`POST /api/v1/categories`:** Cria nova categoria. Role: `ADMIN`.
  - **Entrada:** `{"name": "Serviços em Nuvem", "categoryType": "PAYABLE", "requiresAttachmentAlways": false, "fixedCost": true}`
  - **Status:** `201 Created`, `422 Unprocessable Entity` (duplicidade no mesmo tipo).

### 4.3 Contrapartes / Clientes e Fornecedores (`/v1/counterparties`)
- **`GET /api/v1/counterparties`:** Lista contrapartes ativas (`200 OK`). Roles: `ADMIN`, `ANALISTA`, `AUDITOR`.
- **`GET /api/v1/counterparties/{id}`:** Busca contraparte por ID (`200 OK`, `404 Not Found`).
- **`POST /api/v1/counterparties`:** Cadastra nova contraparte. Roles: `ADMIN`, `ANALISTA`.
  - **Entrada:** `{"legalName": "Amazon Web Services Inc", "tradeName": "AWS", "taxId": "12.345.678/0001-90", "counterpartyType": "SUPPLIER"}`
  - **Status:** `201 Created`, `422 Unprocessable Entity` (CNPJ/CPF duplicado).

---

## 5. Simulação Estocástica de Risco / Monte Carlo HPC (`/v1/simulations`)

Controlador: `SimulationControllerOpenApi` / `SimulationController`

### 5.1 `POST /api/v1/simulations`
- **Descrição:** Dispara uma nova simulação de Monte Carlo de alto desempenho (100.000 a 1.000.000 de cenários) para projetar trajetórias estocásticas de liquidez. Executa primariamente no motor HPC em C++20 via gRPC ou no fallback paralelo JVM.
- **Autorização:** Exclusivo para perfil `ADMIN`.
- **Entrada (Request Body Opcional - `application/json`):**
  ```json
  {
    "horizonDays": 60,
    "numScenarios": 200000,
    "expectedDefaultRate": 8.0,
    "variableCostShockPct": 15.0,
    "randomSeed": 42
  }
  ```
- **Saídas e Códigos de Status:**
  - **`201 Created`:** Simulação concluída e persistida com sucesso.
    ```json
    {
      "id": "0192c8a4-0000-7000-8000-000000000777",
      "horizonDays": 60,
      "numScenarios": 200000,
      "var99": -14250.80,
      "medianP50": 68420.00,
      "ruinProbabilityPct": 4.2500,
      "executionDevice": "OpenCL-NVIDIA-GeForce",
      "executionTimeMs": 85,
      "dailyBands": [...]
    }
    ```
  - **`403 Forbidden`:** Perfil não autorizado.
  - **`422 Unprocessable Entity`:** Horizonte diferente de 30, 60 ou 90 dias, ou cenários fora do intervalo [100.000, 1.000.000].
  - **`503 Service Unavailable`:** Limite de concorrência simultânea esgotado no semáforo de proteção.

### 5.2 `GET /api/v1/simulations/latest`
- **Descrição:** Recupera os resultados e percentis diários da última simulação de risco persistida.
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Saídas e Códigos de Status:**
  - **`200 OK`:** Retorna `SimulationResponse`.
  - **`404 Not Found`:** Nenhuma simulação executada previamente.

### 5.3 `GET /api/v1/simulations/{id}`
- **Descrição:** Recupera uma simulação específica pelo seu identificador UUID.
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Saídas e Códigos de Status:**
  - **`200 OK`:** Retorna `SimulationResponse`.
  - **`404 Not Found`:** Simulação não encontrada.

---

## 6. Dashboard Executivo e Cone de Incerteza (`/v1/dashboard`)

Controlador: `DashboardControllerOpenApi` / `DashboardController`

### 6.1 `GET /api/v1/dashboard/kpis`
- **Descrição:** Consolida os indicadores executivos em tempo real (saldo consolidado em caixa, recebíveis e contas a pagar do dia, e taxa mensal de inadimplência).
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Parâmetros:** `referenceDate` (opcional, default `hoje`).
- **Saídas e Códigos de Status:**
  - **`200 OK`:** Retorna `ExecutiveKpiResponse`:
    ```json
    {
      "consolidatedBalance": { "amount": 154320.50, "currency": "BRL" },
      "totalReceivableToday": { "amount": 18200.00, "currency": "BRL" },
      "totalPayableToday": { "amount": 9450.75, "currency": "BRL" },
      "monthlyDelinquencyRatePct": { "value": 4.2500 },
      "referenceDate": "2026-10-26"
    }
    ```

### 6.2 `GET /api/v1/dashboard/uncertainty-cone`
- **Descrição:** Retorna a série histórica real de saldos combinada com a projeção estocástica do Cone de Incerteza (percentis `P01`, `P05`, `P25`, `P50`, `P75`, `P95`).
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Parâmetros:**
  - `historyDays` (opcional, padrão 30 dias, máx. 365).
  - `simulationId` (opcional; se omitido, vincula à simulação mais recente).
  - `referenceDate` (opcional).
- **Saídas e Códigos de Status:**
  - **`200 OK`:** Retorna `UncertaintyConeResponse` contendo `historicalSeries` e `projectionCone`.

---

## 7. Exportação de Relatórios Financeiros

Controlador: `ReportExportControllerOpenApi` / `ReportExportController`

### 7.1 `GET /api/v1/reports/entries/export` e `POST /api/v1/reports/export`
- **Descrição:** Exporta o livro-razão de lançamentos financeiros em formato tabular (CSV via streaming com limite de memória ou PDF executivo com layout corporativo formatado).
- **Autorização:** `ADMIN`, `ANALISTA` ou `AUDITOR`.
- **Parâmetros / Body:**
  - `format`: `CSV` ou `PDF` (default `CSV`).
  - Filtros: `entryType`, `status`, `categoryId`, `accountId`, `dueDateFrom`, `dueDateTo`, `search`.
- **Saídas e Códigos de Status:**
  - **`200 OK`:** Retorna arquivo binário com `Content-Disposition: attachment; filename="relatorio-financeiro-*.csv|pdf"`.
  - **`400 Bad Request`:** Formato de exportação inválido ou não suportado.

### 7.2 Rotas Dedicadas de Atalho
- **`GET /api/v1/entries/export/csv`:** Exportação direta para CSV (`text/csv`).
- **`GET /api/v1/entries/export/pdf`:** Exportação direta para PDF (`application/pdf`).

---

## 8. Auditoria e Rastreabilidade

Controlador: `AuditLogControllerOpenApi` / `AuditLogController`

### 8.1 `GET /api/v1/audit-logs`
- **Descrição:** Consulta paginada do registro de auditoria imutável, permitindo filtrar por entidade, ator, ação executada e intervalo temporal UTC.
- **Autorização:** `ADMIN` ou `AUDITOR`.
- **Parâmetros de Consulta:**
  - `entityType` (ex.: `FINANCIAL_ENTRY`, `FINANCIAL_ACCOUNT`, `CATEGORY`).
  - `entityId`, `actorUserId` (UUID).
  - `action`: `CREATE`, `UPDATE`, `SETTLE`, `PARTIAL_SETTLE_SPLIT`, `CANCEL_SOFT_DELETE`, `AUTO_OVERDUE_TRANSITION`, `UPLOAD_ATTACHMENT`.
  - `startDate`, `endDate` (ISO 8601 Instant UTC).
  - `page` (default `0`), `size` (default `20`).
- **Saídas e Códigos de Status:**
  - **`200 OK`:** `PagedResult<AuditLogItemResponse>` contendo snapshots antes/depois (`previousState` / `newState`), IP do cliente e correlationId.
  - **`403 Forbidden`:** Perfil `ANALISTA` tentou acessar a trilha de auditoria.

### 8.2 `GET /api/v1/entries/{entryId}/audit-history`
- **Descrição:** Retorna a linha do tempo cronológica com todas as transições de estado pelas quais um lançamento financeiro passou desde a sua criação.
- **Autorização:** `ADMIN` ou `AUDITOR`.
- **Saídas e Códigos de Status:**
  - **`200 OK`:** Retorna `List<AuditLogItemResponse>`.
  - **`404 Not Found`:** Lançamento não encontrado.
