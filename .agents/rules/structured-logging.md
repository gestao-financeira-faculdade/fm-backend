---
trigger: glob
globs: "*.java, **/*.java, src/main/resources/application*.yaml, src/main/resources/application*.yml, src/main/resources/logback*.xml"
description: "Padrão de Observabilidade e Structured Logging (JSON, SLF4J 2 fluent API, OpenTelemetry trace_id/span_id e segurança de logs)."
---

# Observabilidade e Structured Logging

Ao adicionar, modificar ou revisar logs e tratamento de erros em arquivos `.java` ou configurações de logging (`application*.yaml`, `logback*.xml`), consulte o guia @../../docs/STRUCTURED_LOGGING.md ([link](../../docs/STRUCTURED_LOGGING.md)).

- Utilize sempre a API fluente do SLF4J 2 (`log.atInfo().addKeyValue("chave", valor).log("Mensagem estática em inglês")`) com chaves em `snake_case`.
- Não adicione logs dentro do pacote `domain`; registre eventos operacionais na camada `application` e erros de fronteira nos adaptadores (`adapter.in` / `adapter.out`).
- Confie na propagação automática de `trace_id` e `span_id` do OpenTelemetry / Micrometer Tracing (nunca gere IDs de trace manualmente).
- Jamais registre segredos, tokens, senhas ou dados pessoais/financeiros sensíveis sem máscara nos logs (seção 5).
