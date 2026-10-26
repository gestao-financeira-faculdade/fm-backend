---
trigger: glob
globs: "*.java, **/*.java"
description: "Guia operacional de Arquitetura Hexagonal (Ports and Adapters) para Java 25. Consultar em tarefas que envolvam decisões arquiteturais em código Java."
---

# Arquitetura Hexagonal em Java

Ao criar, modificar, refatorar ou revisar componentes `.java` que envolvam decisões arquiteturais (domínio, casos de uso, portas, adaptadores, limites transacionais, organização de pacotes ou estratégia de testes), consulte o guia @../../docs/HEXAGONAL_ARCHITECTURE.md ([link](../../docs/HEXAGONAL_ARCHITECTURE.md)) antes de decidir a implementação.

- Leia primeiro a seção 1 (regras de decisão e desambiguação) e depois apenas as seções pertinentes à tarefa.
- Preserve a arquitetura existente quando adequada à tarefa: não imponha a arquitetura hexagonal a módulos ou pacotes que não a utilizem e não realize migrações ou refatorações arquiteturais fora do escopo solicitado.
- Aplique as recomendações somente ao código alterado ou revisado; ao identificar violações arquiteturais fora do escopo da tarefa, apenas comunique-as com a prioridade indicada no guia (seção 6).
- Em tarefas que envolvam Java e outras tecnologias, aplique o guia apenas às decisões arquiteturais relevantes de código Java.
