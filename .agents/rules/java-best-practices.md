---
trigger: glob
globs: "*.java, **/*.java"
description: "Boas práticas e anti-patterns Java (referência Java 25). Consultar ao criar, modificar, refatorar ou revisar arquivos Java."
---

# Código Java

Ao criar, modificar, refatorar ou revisar arquivos `.java`, consulte o guia @../../docs/JAVA_BEST_PRACTICES.md ([link](../../docs/JAVA_BEST_PRACTICES.md)) antes de decidir a implementação.

- Leia primeiro a seção 1 (regras de decisão) e depois apenas as seções pertinentes à tarefa.
- Verifique a versão Java efetivamente suportada pelo projeto (seção 3) antes de usar qualquer recurso da linguagem; não use recursos em prévia ou incubação sem `--enable-preview` já configurado.
- Aplique as recomendações somente ao código alterado ou revisado. Não refatore código fora do escopo da tarefa; comunique problemas encontrados com a prioridade indicada no guia.
- Em tarefas que envolvem Java e outras tecnologias, aplique o guia apenas às decisões sobre código Java.
