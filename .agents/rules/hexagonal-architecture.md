---
trigger: glob
globs: "*.java, **/*.java"
description: "Guia operacional de Arquitetura Hexagonal (Ports and Adapters) para Java 25. Consultar em tarefas que envolvam decisões arquiteturais em código Java."
---

# Arquitetura Hexagonal em Java

Ao criar, modificar, refatorar ou revisar componentes `.java` que envolvam decisões arquiteturais (domínio, casos de uso, portas, adaptadores, limites transacionais, organização de pacotes ou estratégia de testes), consulte o guia @../../docs/HEXAGONAL_ARCHITECTURE.md ([link](../../docs/HEXAGONAL_ARCHITECTURE.md)) antes de decidir a implementação.

- Leia primeiro a seção 1 (regras de decisão e desambiguação) e depois apenas as seções pertinentes à tarefa.
- A Arquitetura Hexagonal (*Ports and Adapters*) é **estritamente obrigatória** em todos os contextos e pacotes do projeto (`shared`, `identity`, `audit`, `entry`, `reference`, `simulation`, `reporting`). É proibido utilizar organização tradicional acoplada em camadas (`Controller` -> `Service` -> `Repository`).
- Caso identifique código que não siga os padrões da Arquitetura Hexagonal (como `domain` com imports de framework, `application` importando `adapter`, `adapter.out` importando `adapter.in`, ou controllers sem separação `*ControllerOpenApi` / `*WebMapper`), trate-o como incorreto e corrija-o para seguir o padrão hexagonal.
- Em tarefas que envolvam Java e outras tecnologias, aplique o guia a todas as decisões arquiteturais relevantes de código Java.
