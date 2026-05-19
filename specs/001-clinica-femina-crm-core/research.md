# Phase 0 — Research

**Feature**: 001-clinica-femina-crm-core
**Date**: 2026-05-19

This document resolves all open unknowns identified in `plan.md` Technical Context. Each section follows the format: **Decision → Rationale → Alternatives**.

---

## R1. Real-time Transport (WebSocket vs SSE vs Polling)

**Decision**: WebSocket via STOMP over Spring Messaging, with SockJS fallback.

**Rationale**:
- NFR-01 requires ≤2s latency for inbound message notification. Long-polling would compound roundtrips; SSE is one-way only (server→client), insufficient for client→server presence/typing/ack signaling.
- WebSocket supports full duplex needed for future features (typing indicators, read receipts).
- STOMP gives us topic/queue semantics aligned with our broadcast needs (`/user/queue/messages`, `/topic/team-presence`).
- Spring Boot ships first-class STOMP support (`@MessageMapping`, `SimpMessagingTemplate`, broker relay to RabbitMQ STOMP plugin).
- Next.js can consume via `@stomp/stompjs` in a Client Component without ejecting from the framework.

**Alternatives considered**:
- **SSE (Server-Sent Events)**: Simpler, no protocol upgrade; but no client-to-server channel — would force REST + SSE hybrid, adding complexity.
- **Long-polling**: Higher infra cost, worse latency, deprecated pattern.
- **Pusher / Ably (SaaS)**: Adds vendor lock-in, monthly cost, and LGPD complications (third-party data processor).
- **Native WebSocket without STOMP**: Loses pub/sub semantics; we'd reinvent topic routing.

---

## R2. JWT Signing Algorithm (HS256 vs RS256)

**Decision**: **RS256** for access tokens; opaque random string for refresh tokens.

**Rationale**:
- RS256 enables key rotation without sharing secrets with verifying parties (useful when future microservices or N8N nodes need to validate tokens locally).
- Public key can be exposed via `/.well-known/jwks.json` for clients.
- Refresh tokens kept opaque + stored hashed in DB so they can be revoked individually.

**Alternatives considered**:
- **HS256**: Symmetric, simpler, but rotates require coordinated secret change across all services.
- **EdDSA (Ed25519)**: Modern, faster, but library support varies; RS256 is universal.

---

## R3. Sensitive-data Encryption Strategy

**Decision**: Column-level **AES-256-GCM** via Hibernate `AttributeConverter` (`AesGcmConverter`). Encryption key in env (`APP_ENCRYPTION_KEY_V1`), loaded from secrets manager. Key-id stored alongside ciphertext to support rotation.

**Rationale**:
- LGPD + Constitution P6: PHI must be encrypted at rest distinct from disk-level encryption (defense in depth).
- Column-level lets us encrypt only what is sensitive (CPF, full birth name, clinical notes, message body bytes flagged as sensitive), keeping non-sensitive columns queryable.
- AES-GCM gives authenticated encryption — detects tampering.
- `AttributeConverter` is transparent to JPA queries (encryption on persist, decryption on load).
- Postgres `pgcrypto` is an alternative but ties encryption to DB layer and complicates app-level audit.

**Alternatives considered**:
- **Postgres `pgcrypto`**: tighter to DB, but app cannot see plaintext for processing without decryption keys in DB role — operational complexity.
- **Disk encryption only (LUKS/RDS encryption)**: insufficient for LGPD audits since DBA has full access to plaintext.
- **Application-level field encryption with envelope encryption (KMS)**: more secure but requires KMS access per query, adds latency. Postpone to v2 if needed.

---

## R4. Message Broker (RabbitMQ vs Kafka vs none)

**Decision**: **RabbitMQ 3.13**.

**Rationale**:
- Use cases: async outbound WhatsApp sends with retry + DLX, broadcast of inbound messages to STOMP subscribers, fan-out to N8N webhook publisher, automation dispatch.
- RabbitMQ + STOMP plugin can also act as the broker relay for Spring's WebSocket layer, unifying our messaging infra.
- Lightweight (single node sufficient for v1; HA cluster for prod).

**Alternatives considered**:
- **Kafka**: overkill — we don't need event sourcing or massive throughput; ops cost too high for v1.
- **No broker** (synchronous everything): violates NFR-01 (real-time push) and creates tight coupling with Meta API latency.
- **Redis pub/sub**: lacks durable retry / DLX; would need separate retry mechanism.

---

## R5. Patient Search Strategy

**Decision**: PostgreSQL **`pg_trgm` extension** + GIN index on `LOWER(patient.full_name)` and `patient.phone_normalized`.

**Rationale**:
- NFR-07 requires ≤1s patient search. Trigram indexes handle fuzzy name search efficiently up to hundreds of thousands of patients.
- Phone search via normalized E.164 column with btree index.
- Stays within Postgres — no need to introduce Elasticsearch in v1.

**Alternatives considered**:
- **Elasticsearch / OpenSearch**: better full-text features but adds ops + sync complexity. Reserve for v2 if needed.
- **LIKE without index**: catastrophic at scale.

---

## R6. Quartz Persistence (in-memory vs JDBC)

**Decision**: **Quartz with JDBC JobStore** in PostgreSQL.

**Rationale**:
- Jobs (reminders, automation, satisfaction) must survive restarts.
- JDBC JobStore reuses the existing Postgres instance, no new infra.
- Spring Boot Starter Quartz integrates seamlessly.

**Alternatives considered**:
- **`@Scheduled` annotation**: not durable, no clustering, no fine-grained control.
- **ShedLock + `@Scheduled`**: simpler for single-master scheduling but lacks Quartz's misfire policies and per-job persistence.

---

## R7. WhatsApp Media Storage

**Decision**: **S3-compatible object storage** with KMS-encrypted bucket. URL is presigned for short windows when serving back to UI.

**Rationale**:
- Storing binaries in Postgres bloats the DB and slows backups.
- S3 / MinIO provides server-side encryption + lifecycle policies (retention per LGPD policy).
- Presigned URLs prevent unauthorized direct access.

**Alternatives considered**:
- **PostgreSQL bytea or large object**: rejected (bloat, backup pain).
- **Filesystem on app server**: rejected (loses on container restart, no HA).

---

## R8. Frontend State Management

**Decision**: **Zustand** (global UI + auth + notification state) + **TanStack Query** (server-state cache).

**Rationale**:
- Zustand is small, RSC-friendly, no provider hell.
- TanStack Query handles cache, refetch, optimistic updates, deduplication.
- Constitution P4 forbids Redux without governed amendment — Zustand satisfies the rule.

**Alternatives considered**:
- **Redux Toolkit**: forbidden by constitution unless amended.
- **React Context only**: not enough for server-state caching.
- **Jotai / Valtio**: comparable to Zustand; Zustand has broader Next.js community examples.

---

## R9. UI Component Library

**Decision**: **shadcn/ui** primitives + Tailwind CSS 4 + Recharts.

**Rationale**:
- shadcn is a copy-in, no-runtime library — gives us ownership of components without dependency bloat.
- Tailwind for utility CSS aligns with Next.js community defaults.
- Recharts is a mature React chart library covering all dashboard needs (line, bar, donut).

**Alternatives considered**:
- **MUI**: heavier runtime, opinionated styling; harder to theme to clinic brand.
- **Chakra UI**: less Next.js App Router maturity at this stage.
- **Apexcharts / Chart.js**: viable; Recharts has nicer React-idiomatic API.

---

## R10. Multi-tenancy Approach (single tenant v1 with future-proofing)

**Decision**: **Single-tenant deployment** for v1 — but every tenant-bearing table includes `clinic_id BIGINT NOT NULL` FK referencing a single `clinic` row. All repositories filter implicitly via a `TenantContextHolder` resolved from JWT claims.

**Rationale**:
- Spec assumption: "Clínica is initially a single tenant."
- Adding `clinic_id` later via migration on populated PHI tables is risky and slow — pay the cost up-front.
- `TenantContextHolder` + Hibernate filter primitives mean future multi-tenant rollout is a configuration change.

**Alternatives considered**:
- **No tenant column**: blocks future multi-tenant without a painful migration.
- **Schema-per-tenant**: overkill for v1, complicates migrations.

---

## R11. AI Window Enforcement (Server-side vs N8N-side)

**Decision**: **Both, defense in depth**. N8N consults the AI window via a thin endpoint before scheduling; the backend re-validates the window when N8N posts the scheduling intent. Out-of-window scheduling attempts are rejected by the backend.

**Rationale**:
- BR-03 mandates the AI never schedules outside the window. Trusting only N8N would violate Constitution P6 (never trust client data server-side).
- Reverse: trusting only the backend means N8N can waste roundtrips. Both sides need the rule.

**Alternatives considered**:
- **Backend-only enforcement**: works but produces noisy rejections in N8N.
- **N8N-only enforcement**: violates principle of least trust.

---

## R12. Soft-delete Implementation

**Decision**: `deleted_at TIMESTAMPTZ NULL` column + Hibernate `@SQLRestriction("deleted_at IS NULL")` on the entity class + `@SoftDelete` semantics in repositories. A `*Repository.findAllIncludingDeleted()` method explicit-opts into seeing deleted rows.

**Rationale**:
- Constitution P3 + BR-02 require soft delete by default with explicit hard-delete flow.
- `@SQLRestriction` is automatic — developers can't accidentally include deleted rows.
- LGPD erasure requires actual deletion path, exposed only via `LgpdController.hardDelete()` guarded by Manager role + audit reason.

**Alternatives considered**:
- **`is_deleted BOOLEAN`**: loses the "when deleted" information.
- **Trigger-based archival to history table**: more complex; revisit if data retention requires it.

---

## R13. OpenJDK Distribution

**Decision**: **Eclipse Temurin 21 LTS** (Adoptium build of OpenJDK).

**Rationale**:
- NFR-09 (RNF06 of PDF) mandates OpenJDK. Eclipse Temurin is a free, TCK-certified OpenJDK build with predictable LTS cadence.
- Wide container image availability.

**Alternatives considered**:
- **Oracle JDK**: not OpenJDK; violates RNF06.
- **Amazon Corretto / Azul Zulu**: also valid OpenJDK builds; Temurin chosen for community neutrality.
- **GraalVM CE**: native-image is interesting but Spring Boot AOT support still maturing in 2025–2026 for our level of reflection (JPA + STOMP) — defer.

---

## R14. CSV/XLSX Export Library

**Decision**: **Apache POI 5.x** for XLSX, **OpenCSV** for CSV.

**Rationale**:
- Native Java options, no licensing concerns.
- FR-LGPD-04 + RNF03 require both formats.

**Alternatives considered**:
- **JExcel**: outdated, no .xlsx support.
- **Fastexcel**: lighter, considered if memory pressure becomes an issue.

---

## R15. Time Zone Strategy

**Decision**: Store all timestamps as `TIMESTAMPTZ` (UTC) in PostgreSQL. Convert at presentation time using `ZoneId.of("America/Sao_Paulo")` (BRT/BRST). JVM runs with `-Duser.timezone=UTC`.

**Rationale**:
- BRT does not observe DST in current Brazilian regulation but the `America/Sao_Paulo` tz handles legacy correctly.
- Frontend resolves locale via browser; backend always returns ISO-8601 with `Z`.

**Alternatives considered**:
- **Store local time**: error-prone; tz rules can change (Brazil DST history demonstrates the risk).

---

## R16. Class-diagram Reconciliation — RESOLVIDO

**Status**: ✅ **RESOLVIDO em 2026-05-19** — diagrama (`diagramadeclassesCRMclinica.pdf`) recebido e reconciliado integralmente.

**Decision (atualizada)**: Modelo de dados reescrito em **v2** alinhado ao Diagrama de Classes UML oficial. Nomenclatura migrada para pt-BR. Diff explícito documentado em [data-model-reconciliation.md](./data-model-reconciliation.md).

**Alterações principais aplicadas**:
- 12 entidades do diagrama → 12 tabelas principais em pt-BR.
- 17 tabelas auxiliares justificadas (LGPD, audit, multi-tenant, refresh_token).
- Adicionado campo `mensagem_rapida.atalho` (faltava no v1).
- Adicionado campo `paciente.notas_internas` (faltava no v1).
- Adicionado campo `agendamento.motivo_cancelamento_ia` inline (espelho de `cancelamento_ia` para query rápida).
- **Atendimento ressemantizado**: deixou de ser thread perpétua (`conversation` v1) para ser **sessão com início/fim** com status `ATIVO`/`TRANSFERIDO`/`ENCERRADO`/`IA_AUTOMATICO`.
- `janela_horario_ia` remodelada: de 1 linha com bitmask para N linhas (uma por dia da semana), conforme diagrama.
- Hierarquia `Usuario` → `Gestor`/`Recepcionista`/`Medico` implementada via single-table inheritance com discriminator `perfil` + tabela auxiliar `perfil_medico` + tabela auxiliar `permissoes_recepcionista`.
- `MedicoRealizaAgendamento` enforced via service + trigger (`medico_id` deve referenciar usuário com `perfil = 'MEDICO'`).

**Alternativas consideradas**:
- **Manter v1 (en) com mapping camada**: rejeitado — viola o princípio "tabelas refletem o domínio". Equipe que lê o diagrama precisa ler tabela com mesmo nome.
- **Reescrever apenas nomes de tabela sem mudar semântica**: rejeitado — `Atendimento` no diagrama tem `dataInicio` + `status` + `transferirAtendente()`, semântica diferente de `Conversation`. Necessário ressemantizar.

---

## R17. Nomenclatura: pt-BR no Domínio, Inglês nos Termos Técnicos

**Decision**: Tabelas, colunas, entidades Java de domínio, endpoints REST de domínio e DTOs ficam em **pt-BR**. Termos técnicos universais (`Controller`, `Service`, `Repository`, `Filter`, `Config`, `refresh_token`, `webhook`, `cron`, `JWT`) ficam em inglês.

**Rationale**:
- Diagrama de Classes oficial está em pt-BR.
- Cliente final (clínica) e equipe de desenvolvimento são brasileiros.
- Documentação interna e nova comunicação textual já são pt-BR (memória de comunicação registrada em 2026-05-19).
- Reduz fricção cognitiva entre artefato de domínio (diagrama, requisitos PDF) e código.

**Aplicação**:
- ✅ Tabelas: `paciente`, `atendimento`, `mensagem`, `agendamento`, ...
- ✅ Colunas: `nome`, `senha_hash`, `data_hora`, `criado_em`, ...
- ✅ Entidades Java: `Paciente`, `Atendimento`, `Mensagem`, ...
- ✅ Endpoints REST de domínio: `/api/pacientes`, `/api/atendimentos`, `/api/agendamentos`, ...
- ❌ Termos técnicos universais permanecem em inglês: `Controller`, `Service`, `Repository`, `JwtAuthenticationFilter`, `WebSocketConfig`.
- ❌ Endpoints de webhook externos mantêm caminho neutro: `/api/webhooks/whatsapp`, `/api/webhooks/n8n/ai-action`.
- ❌ Constantes técnicas: `JWT`, `STOMP`, `HMAC`, `WebSocket`, `RabbitMQ`, `Quartz`.

**Alternativas consideradas**:
- **Tudo em inglês**: rejeitado — desalinha do diagrama oficial e da comunicação cliente.
- **Tudo em pt-BR (incluindo termos técnicos)**: rejeitado — termos como "Controlador", "Repositório" criam fricção desnecessária com a comunidade Spring/Java.

---

## Resolution of Spec §11 Items

| Spec item | Resolution in plan |
|-----------|--------------------|
| Multi-tenancy | R10 — single tenant v1, `clinic_id` column primed. |
| Satisfaction-survey interval | Default 4h (Manager-configurable in `automation_rule` row). |
| Inactive-patient threshold | Default 90 days (Manager-configurable in `automation_rule` row). |

---

## Summary

All Technical Context unknowns resolved. No remaining `NEEDS CLARIFICATION` markers. Architecture is ready for the data-model + contracts phase.
