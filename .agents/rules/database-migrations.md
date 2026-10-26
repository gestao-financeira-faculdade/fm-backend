---
trigger: glob
globs: "*.sql, **/*.sql, src/main/resources/db/migration/**, *Entity.java, **/*Entity.java, *Repository*.java, **/*Repository*.java"
description: "Regras e boas práticas de migrações de banco de dados (Flyway e PostgreSQL). Consultar ao criar ou revisar scripts SQL, entidades de persistência ou repositórios."
---

# Migrações de Banco de Dados (Flyway e PostgreSQL)

Ao criar, modificar ou revisar scripts `.sql` em `src/main/resources/db/migration/`, entidades JPA (`*Entity.java`) ou repositórios/queries jOOQ que dependam do esquema do banco, consulte o guia @../../docs/DATABASE_MIGRATIONS.md ([link](../../docs/DATABASE_MIGRATIONS.md)) antes de decidir a implementação.

- Leia primeiro as seções 1 a 5 (regras de decisão, nomenclatura `V1__...sql`, mapeamento de tipos e armadilhas não óbvias entre PostgreSQL e Hibernate).
- Nunca altere ou exclua uma migração `V*__*.sql` já commitada; crie sempre uma nova migração sequencial (`V<N+1>__<descricao_snake_case>.sql`).
- Use `UUID` (preferindo UUIDv7), nomes de tabelas no plural em `snake_case`, `NUMERIC(19, 2)` para valores monetários, `VARCHAR(n)` (nunca `CHAR(n)`), `TIMESTAMPTZ` para instantes de tempo e crie índices explícitos para todas as chaves estrangeiras.
- Sempre valide novas migrações executando os testes com PostgreSQL via Testcontainers (`./mvnw test`).
