---
trigger: always_on
description: "RTK (Rust Token Killer) Universal CLI proxy for token-optimized command execution across all technology stacks."
---

# RTK (Rust Token Killer) - Universal Execution Rules

Always prefix external CLI commands with `rtk` across frontend, backend, devops and data operations to compress verbose logs and save context tokens:

- **Version Control**: `rtk git status`, `rtk git diff`, `rtk git log`, `rtk git add`, `rtk git commit`, `rtk gh pr list`
- **Frontend / JS / TS**: `rtk npm ...`, `rtk pnpm ...`, `rtk bun ...`, `rtk npx ...`, `rtk tsc`, `rtk vitest`, `rtk jest`, `rtk playwright test`
- **Backend / JVM / Rust / Python / .NET**: `rtk ./mvnw ...`, `rtk mvn ...`, `rtk ./gradlew ...`, `rtk cargo ...`, `rtk pytest`, `rtk dotnet ...`, `rtk ctest`
- **Containers & Cloud**: `rtk docker ...`, `rtk kubectl ...`, `rtk aws ...`
- **Search & Utilities**: `rtk rg ...`, `rtk grep ...`, `rtk find ...`, `rtk diff ...`, `rtk curl ...`
- **Error & Test Wrappers**: `rtk err <cmd>`, `rtk test <cmd>`
- **Chains**: `rtk git add . && rtk git commit -m "feat: message"`

## Windows / PowerShell Specifics
- Commands that invoke external binaries are routed through `rtk` (`git`, `gh`, `cargo`, `docker`, `mvn`, `./mvnw`, `npm`, `pnpm`, `python`, etc.).
- Do NOT prefix PowerShell built-in cmdlets or script blocks with `rtk` (e.g. use `Get-ChildItem`, `Test-Path`, `$env:VAR = ...` directly, not `rtk ls` or `rtk Get-ChildItem`).
- If raw unfiltered output is ever needed, run `rtk proxy <cmd>`.
