# AGENTS.md — Agent Context

This file gives Codex (and other AI agents) the working context for the Clínica Femina CRM project.

## Communication Language

**Sempre responder ao usuário em português brasileiro (pt-BR).**

- Toda comunicação textual (explicações, resumos, status, perguntas) deve ser em pt-BR.
- Código, mensagens de commit, identificadores e documentos técnicos já escritos em inglês (spec.md, plan.md, etc.) permanecem inalterados — não traduzir retroativamente sem pedido explícito.
- Novos documentos voltados para leitura humana (READMEs, notas de onboarding) devem ser em pt-BR por padrão.

## Project

**Clínica Femina CRM** — multi-profile (Gestor / Recepcionista / Médico) clinic platform for prenatal patient management via WhatsApp. Integrates Meta WhatsApp Cloud API, N8N (AI orchestration), and Darwin (read-only clinical data). LGPD-compliant by design.

## Stack

- **Backend**: Java 21 (Temurin OpenJDK) + Spring Boot 3.3.x + PostgreSQL 16 + Flyway + RabbitMQ + Quartz.
- **Frontend**: Next.js 15 (App Router, RSC) + TypeScript 5 + Tailwind 4 + shadcn/ui + Zustand + TanStack Query.
- **Realtime**: WebSocket + STOMP (Spring Messaging, broker relay via RabbitMQ).
- **Security**: JWT (RS256) + BCrypt 12 + AES-256-GCM column-level encryption + HttpOnly refresh cookies.

## Constitution

Read [.specify/memory/constitution.md](./.specify/memory/constitution.md) before proposing architectural changes. Key non-negotiables:

- Strict layering: Controller → Service → Repository → Entity.
- Constructor DI only.
- DTOs at every boundary.
- Server Components by default in Next.js.
- No PII in logs.
- BCrypt cost ≥ 12.
- Parameterized SQL only.
- ≥ 80% test coverage on Service + Controller layers.

## Speckit Artifacts (Current Feature)

<!-- SPECKIT START -->
- **Active feature**: `001-clinica-femina-crm-core`
- **Spec**: [specs/001-clinica-femina-crm-core/spec.md](./specs/001-clinica-femina-crm-core/spec.md)
- **Plan**: [specs/001-clinica-femina-crm-core/plan.md](./specs/001-clinica-femina-crm-core/plan.md) (v2 reconciliado)
- **Research**: [specs/001-clinica-femina-crm-core/research.md](./specs/001-clinica-femina-crm-core/research.md)
- **Data model**: [specs/001-clinica-femina-crm-core/data-model.md](./specs/001-clinica-femina-crm-core/data-model.md) (v2 — pt-BR + Diagrama)
- **Reconciliação**: [specs/001-clinica-femina-crm-core/data-model-reconciliation.md](./specs/001-clinica-femina-crm-core/data-model-reconciliation.md)
- **Contracts**: [specs/001-clinica-femina-crm-core/contracts/](./specs/001-clinica-femina-crm-core/contracts/) (pt-BR)
- **Quickstart**: [specs/001-clinica-femina-crm-core/quickstart.md](./specs/001-clinica-femina-crm-core/quickstart.md)
- **Diagrama de Classes**: `diagramadeclassesCRMclinica.pdf` (fonte de verdade do domínio)
<!-- SPECKIT END -->

## Pointer File

`.specify/feature.json` holds the resolved feature directory for downstream commands:
```json
{ "feature_directory": "specs/001-clinica-femina-crm-core" }
```

## Source-of-truth Documents

- **PDF de Requisitos** — `Documentacao_Requisitos_ClinicaFemina_CRM 2.pdf` (v1.5) — fonte de verdade dos requisitos funcionais e RNF
- **Diagrama de Classes** — `diagramadeclassesCRMclinica.pdf` — fonte de verdade do modelo de domínio (entidades, atributos, relacionamentos, métodos)
- **MVP screens** — `Inspirações/` (10 PNGs) — referência visual; ~80% reflete o escopo final, 20% descartado conforme PDF

**Hierarquia de autoridade**:
1. Diagrama de Classes (modelo de domínio)
2. PDF de Requisitos (requisitos)
3. MVP screens (UX/visual)
4. Spec/Plan deste projeto (consolidação)

## Conventions

- **Nomenclatura de domínio em pt-BR** alinhada ao Diagrama de Classes:
  - Tabelas: `paciente`, `atendimento`, `mensagem`, `agendamento`, `lembrete`, `mensagem_rapida`, `regra_automacao`, `janela_horario_ia`, `tag`, `pesquisa_satisfacao`, `usuario`, etc.
  - Entidades Java: `Paciente`, `Atendimento`, `Mensagem`, etc.
  - Endpoints REST de domínio: `/api/pacientes`, `/api/atendimentos`, `/api/agendamentos`, ...
- Termos técnicos universais ficam em inglês: `Controller`, `Service`, `Repository`, `Filter`, `Config`, `JWT`, `STOMP`, `refresh_token`, `webhook`.
- All timestamps stored UTC, presented in `America/Sao_Paulo`.
- All money amounts in BRL with 2 decimals.
- All identifiers are int64 (BIGSERIAL).
- All sensitive columns marked 🔒 in `data-model.md` are AES-256-GCM encrypted at column level.
- Default UI language: pt-BR.

## What This Feature Does NOT Include

- Mobile-first responsive UI (PT01 — backlog).
- Full Follow-UP automation engine (PT02 — backlog; status label only in v1).
- Bidirectional Darwin sync (read-only only).
- Multi-tenant clinic platform (single-tenant; primitives in place).
