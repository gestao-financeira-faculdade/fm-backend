---
trigger: glob
globs: "*Test*.java, **/*Test*.java, src/test/**"
description: "Guia oficial de Qualidade e Estratégia de Testes (Unitários, Integração e E2E) do projeto gestao-financeira. Consultar ao criar, modificar, refatorar ou revisar testes automatizados."
---

# Qualidade e Estratégia de Testes

Ao criar, modificar, refatorar ou revisar testes automatizados (`src/test/**` ou arquivos `*Test*.java`), consulte o guia @../../docs/TESTING_STRATEGY.md ([link](../../docs/TESTING_STRATEGY.md)) antes de decidir a implementação.

- Leia primeiro a seção 1 (princípios gerais) e a seção 5 (matriz comparativa e critérios de decisão) para escolher a camada de teste adequada (Unitário, Integração de Fronteira ou End-to-End).
- Em testes unitários de domínio (`domain`) e casos de uso (`application`), não suba o contexto do Spring (`@SpringBootTest`); instancie as classes diretamente via construtor com `Clock.fixed(...)` e utilize *In-Memory Fakes* para portas de repositório (seção 2).
- Em testes de adaptadores de persistência (`adapter.out.persistence`), nunca mocke `JpaRepository`, `EntityManager` ou `DSLContext`; utilize `@DataJpaTest` ou `@JooqTest` combinados com `@Import(TestcontainersConfiguration.class)` contra o PostgreSQL real (seção 3).
- Aplique as recomendações apenas aos testes criados, alterados ou revisados na tarefa. Ao identificar anti-patterns de testes fora do escopo, comunique-os com o código (`QA-01` a `QA-08`) e a prioridade indicada na seção 6 do guia.
