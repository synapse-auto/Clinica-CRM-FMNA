# Implementation Plan: Clínica Femina CRM — Core Platform

**Feature ID**: 001-clinica-femina-crm-core
**Spec**: [spec.md](./spec.md)
**Created**: 2026-05-19
**Last Updated**: 2026-05-19 (v2 — reconciliado com Diagrama de Classes UML)
**Status**: Draft (Phase 1 complete, ready for `/speckit-tasks`)
**Constitution**: [.specify/memory/constitution.md](../../.specify/memory/constitution.md) v1.0.0
**Reconciliação**: [data-model-reconciliation.md](./data-model-reconciliation.md)

---

## 1. Summary

The Clínica Femina CRM is a multi-profile (Manager / Receptionist / Doctor) clinic platform with WhatsApp-first patient communication, real-time chat, AI-assisted scheduling (via N8N), and full LGPD compliance. This plan defines the technical architecture, layered structure, persistence model, real-time strategy, integration boundaries, and security posture for the implementation phase.

---

## 2. Technical Context

| Item | Decision | Source |
|------|----------|--------|
| **Backend language** | Java 21 LTS (OpenJDK distribution) | User directive + NFR-09 |
| **Backend framework** | Spring Boot 3.3.x (LTS line) | User directive |
| **Frontend framework** | Next.js 15 (App Router) + React 19 | User directive |
| **Frontend language** | TypeScript 5.x (strict mode) | Industry default for Next.js + Constitution P1 |
| **Database** | PostgreSQL 16 | User directive |
| **DB migration tool** | Flyway | Constitution P3 |
| **API style** | REST + JSON (OpenAPI 3.1 spec) | Constitution P3 |
| **Real-time protocol** | WebSocket via STOMP (Spring Messaging) | NFR-01 (≤ 2s latency) — see Phase 0 research |
| **Auth** | JWT (HS256/RS256) + Refresh Token in HttpOnly cookie | NFR-02 + Constitution P6 |
| **Password hashing** | BCrypt cost 12 | NFR-03 (PDF: ≥ 10) + Constitution P6 (≥ 12) |
| **Sensitive data encryption** | AES-256-GCM (column-level via Hibernate `AttributeConverter`) | NFR-02 + LGPD |
| **Message broker** | RabbitMQ 3.13 (async automation, scheduled jobs, webhook fan-out) | See Phase 0 research |
| **Job scheduler** | Quartz (Spring Boot starter) for reminders, automation, satisfaction surveys | NFR-01 + FR-AUT-* |
| **Search / lookup** | PostgreSQL `pg_trgm` + GIN index on patient name/phone | NFR-07 (≤ 1s) |
| **Object storage (media)** | S3-compatible (AWS S3 or MinIO for on-prem) | RF15 WhatsApp media |
| **Validation** | Jakarta Bean Validation (backend) + Zod (frontend) | Constitution P6 |
| **State management (FE)** | Zustand + TanStack Query | Constitution P4 |
| **Component library** | shadcn/ui + Tailwind CSS 4 | UI consistency |
| **WebSocket client (FE)** | `@stomp/stompjs` over native WebSocket | Compatible with Spring STOMP |
| **Charts** | Recharts | Dashboard rendering |
| **Testing — BE** | JUnit 5, Mockito, Testcontainers (PostgreSQL + RabbitMQ) | Constitution P7 |
| **Testing — FE** | Vitest + React Testing Library + Playwright (E2E) | Constitution P7 |
| **Containerization** | Docker + Docker Compose (dev), Helm chart (prod, optional) | Standard deployment |
| **Observability** | SLF4J + Logback (JSON layout), Micrometer → Prometheus, OpenTelemetry traces | Constitution P8 |
| **CI/CD** | GitHub Actions (build, test, lint, OWASP dependency check) | Constitution P6 |
| **Time zone** | `America/Sao_Paulo` (BRT/BRST) | Spec Assumptions |
| **i18n** | pt-BR only (v1) | Spec Assumptions |

No `[NEEDS CLARIFICATION]` items remain. All open spec questions resolved in research.md.

---

## 3. Constitution Check (Pre-Design Gate)

| Principle | Compliance | Notes |
|-----------|------------|-------|
| P1 Clean Code | ✅ | Plan keeps responsibilities split per layer; no god services planned. |
| P2 DRY | ✅ | Shared utilities for DTO mapping (MapStruct), validation, exception handling. |
| P3 Spring Boot | ✅ | Controller→Service→Repository→Entity strict layering; constructor DI; DTOs at boundary; `@Transactional` on Service; Flyway for schema. |
| P4 Next.js | ✅ | Server Components by default; client components only for chat, charts, real-time; `next/image`, `next/link`. |
| P5 Organization | ✅ | Package layout and folder structure declared §4.2 / §4.3 below. |
| P6 Security | ✅ | JWT + HttpOnly cookie + BCrypt 12 + AES-256-GCM + Bean Validation + Zod + RBAC + tenant isolation (single-tenant in v1, but isolation primitives present). |
| P7 Testing | ✅ | Unit on Service, integration via Testcontainers on Controller, E2E via Playwright on critical flows. |
| P8 Observability | ✅ | Structured JSON logs, correlation ID via `X-Request-Id`, Micrometer metrics on critical paths, no PII in logs. |

**Gate result**: PASS — no waivers required.

---

## 4. Architecture

### 4.1 High-level Architecture

```
                         ┌──────────────────────┐
                         │  Patients (WhatsApp) │
                         └──────────┬───────────┘
                                    │ Meta WhatsApp Cloud API
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Edge / Reverse Proxy                       │
│                       (NGINX / Cloudflare)                       │
└─────────┬─────────────────────────────────────┬─────────────────┘
          │ HTTPS                                │ WSS (STOMP)
          ▼                                      ▼
┌─────────────────────┐               ┌──────────────────────┐
│  Next.js Frontend   │   REST/JSON   │  Spring Boot API     │
│  (SSR + RSC + CSR)  │◄─────────────►│  Stateless x N pods  │
│  Server Actions     │               │                      │
└─────────────────────┘               └────────┬─────────────┘
                                               │
        ┌──────────────────────────────────────┼─────────────────────────────┐
        ▼                                      ▼                             ▼
┌────────────────┐     ┌────────────────┐ ┌──────────────┐         ┌──────────────────┐
│  PostgreSQL 16 │     │  RabbitMQ 3.13 │ │  S3 / MinIO  │         │  Quartz Scheduler │
│  (primary +    │     │  (async jobs,  │ │  (WhatsApp   │         │  (reminders,      │
│   read replica)│     │   webhook fan- │ │  media, LGPD │         │   automation,     │
│  + Flyway      │     │   out, retries)│ │  exports)    │         │   satisfaction)   │
└────────────────┘     └─────┬──────────┘ └──────────────┘         └──────────────────┘
                             │
        ┌────────────────────┼──────────────────────────────────────┐
        ▼                    ▼                                      ▼
┌──────────────────┐  ┌────────────────┐                  ┌─────────────────────┐
│  N8N (external)  │  │  Darwin API    │                  │  Meta WhatsApp Cloud │
│  AI orchestration│  │  (read-only)   │                  │  API (in/outbound)   │
│  inbound webhooks│  │  cron sync     │                  │  webhook + send      │
└──────────────────┘  └────────────────┘                  └─────────────────────┘
```

### 4.2 Backend Package Layout (Spring Boot) — pt-BR alinhado ao Diagrama de Classes

Per Constitution P5. Nomes de entidades de domínio em pt-BR para casar com o Diagrama. Termos técnicos universais (Controller, Service, Repository, Config, Filter) ficam em inglês.

```
src/main/java/com/synapse/clinicafemina/
├── ClinicaFeminaApplication.java
├── config/
│   ├── SecurityConfig.java
│   ├── JwtConfig.java
│   ├── WebSocketConfig.java
│   ├── RabbitConfig.java
│   ├── OpenApiConfig.java
│   ├── CorsConfig.java
│   └── PersistenceConfig.java
├── controller/
│   ├── AuthController.java
│   ├── AtendimentoController.java        # ex-ConversationController (Diagrama: Atendimento)
│   ├── MensagemController.java
│   ├── PacienteController.java
│   ├── AgendamentoController.java
│   ├── LembreteController.java
│   ├── TagController.java
│   ├── MensagemRapidaController.java
│   ├── EquipeController.java
│   ├── AutomacaoController.java
│   ├── HorarioController.java            # JanelaHorarioIA + HorarioAtendente
│   ├── DashboardController.java
│   ├── AgendaController.java
│   ├── ConfiguracoesController.java
│   ├── SatisfacaoController.java
│   ├── CancelamentoIaController.java
│   ├── LgpdController.java               # consentimento, exportação, eliminação
│   └── webhook/
│       ├── WhatsappWebhookController.java
│       └── N8nWebhookController.java
├── service/
│   ├── auth/         AuthService, JwtService, RefreshTokenService
│   ├── atendimento/  AtendimentoService, MensagemService, RealtimeBroadcastService
│   ├── paciente/     PacienteService, PacienteBuscaService
│   ├── agendamento/  AgendamentoService, AgendamentoTransicaoService
│   ├── lembrete/     LembreteService, LembreteScheduler
│   ├── tag/          TagService
│   ├── mensagemrapida/ MensagemRapidaService
│   ├── equipe/       EquipeService
│   ├── automacao/    RegraAutomacaoService, AutomacaoDispatcher
│   ├── horario/      JanelaHorarioIaService, HorarioAtendenteService
│   ├── dashboard/    DashboardMetricasService
│   ├── agenda/       AgendaService
│   ├── satisfacao/   SatisfacaoService
│   ├── ia/           CancelamentoIaService
│   ├── lgpd/         ConsentimentoService, PortabilidadeDadosService, AuditoriaService
│   └── integration/
│       ├── whatsapp/  WhatsappClient, WhatsappOutboundService, WhatsappMediaService
│       ├── n8n/       N8nWebhookPublisher
│       └── darwin/    DarwinClient, DarwinSyncJob
├── repository/       *Repository interfaces (Spring Data JPA — UsuarioRepository, PacienteRepository, AtendimentoRepository, ...)
├── entity/           # JPA entities em pt-BR — 1:1 com data-model.md v2
│   ├── Usuario.java                    # @Inheritance(SINGLE_TABLE) + @DiscriminatorColumn("perfil")
│   ├── Gestor.java extends Usuario
│   ├── Recepcionista.java extends Usuario
│   ├── Medico.java extends Usuario
│   ├── PerfilMedico.java
│   ├── Paciente.java
│   ├── Atendimento.java                # ex-Conversation
│   ├── TransferenciaAtendimento.java
│   ├── Mensagem.java
│   ├── MidiaMensagem.java
│   ├── Agendamento.java
│   ├── CancelamentoIa.java
│   ├── JanelaHorarioIa.java
│   ├── Lembrete.java
│   ├── MensagemRapida.java
│   ├── Tag.java
│   ├── RegraAutomacao.java
│   ├── LogAutomacao.java
│   ├── PesquisaSatisfacao.java
│   ├── Consentimento.java
│   ├── LogAuditoria.java
│   ├── RelatorioIncidente.java
│   ├── EstadoSyncDarwin.java
│   ├── Clinica.java
│   └── ... (HorarioAtendente, CapacidadeUsuario, PermissoesRecepcionista, RefreshToken, etc.)
├── dto/
│   ├── request/  PacienteRequest, AgendamentoRequest, MensagemRequest, ...
│   └── response/ PacienteResponse, AtendimentoResponse, ...
├── mapper/           MapStruct mappers (PacienteMapper, AtendimentoMapper, ...)
├── exception/
│   ├── ApiException.java
│   ├── RecursoNaoEncontradoException.java
│   ├── AcessoNegadoException.java
│   ├── RegraNegocioVioladaException.java
│   └── GlobalExceptionHandler.java
├── security/
│   ├── JwtAuthenticationFilter.java
│   ├── JwtAuthenticationProvider.java
│   ├── RbacAuthorizationManager.java
│   └── HorarioAtendenteGuard.java       # bloqueia acesso fora de horário
├── crypto/
│   └── AesGcmConverter.java             # AttributeConverter for AES-256-GCM
├── audit/
│   └── DadoPessoalAccessAspect.java     # AOP para audit LGPD
└── util/
```

### 4.3 Frontend Folder Layout (Next.js)

Per Constitution P5:

```
src/
├── app/
│   ├── (auth)/
│   │   ├── login/page.tsx
│   │   └── layout.tsx
│   ├── (main)/
│   │   ├── layout.tsx
│   │   ├── atendimentos/page.tsx     # default landing
│   │   ├── dashboard/page.tsx
│   │   ├── agenda/page.tsx
│   │   ├── pacientes/
│   │   │   ├── page.tsx
│   │   │   └── kanban/page.tsx
│   │   ├── equipe/page.tsx
│   │   ├── automacao/page.tsx
│   │   ├── tags/page.tsx
│   │   ├── msgs-rapidas/page.tsx
│   │   ├── horarios/page.tsx
│   │   ├── configuracoes/page.tsx
│   │   ├── satisfacao/page.tsx
│   │   └── cancelamentos-ia/page.tsx
│   ├── loading.tsx
│   ├── error.tsx
│   ├── globals.css
│   └── layout.tsx
├── components/
│   ├── ui/                # shadcn primitives
│   └── features/
│       ├── chat/          ConversationList, ChatPanel, ContextPanel, QuickMessagePicker
│       ├── kanban/        KanbanBoard, KanbanCard
│       ├── dashboard/     KpiCard, MessagesByHourChart, ServiceDistributionChart, ...
│       ├── agenda/        WeekGrid, DoctorBreakdown
│       ├── automation/    RuleCard, RuleEditor
│       ├── tags/          TagCard, TagEditor
│       └── lgpd/          ConsentBanner, ExportDialog
├── hooks/                 useRealtimeChannel, useConversation, useRequireRole, ...
├── services/              REST clients (one per backend controller area)
├── store/                 Zustand stores (auth, conversation, notification)
├── types/                 TypeScript types + Zod schemas
└── lib/                   utils, fetcher, jwt-helpers, ws-client
```

### 4.4 Layered Flow (per request)

For an authenticated user request (Constitution P3 strict):

```
HTTP request
   ↓ JwtAuthenticationFilter (validate token)
   ↓ AttendantScheduleGuard (block out-of-hours)
   ↓ @PreAuthorize on Controller method (role + capability check)
Controller
   ↓ map RequestDTO → domain via MapStruct
   ↓ delegate to Service (constructor-injected)
Service (@Transactional)
   ↓ orchestrate business rules
   ↓ Repository (JPA) for persistence
   ↓ AesGcmConverter on sensitive fields
   ↓ PersonalDataAccessAspect logs LGPD audit
Repository → PostgreSQL
   ↓ result mapped via MapStruct → ResponseDTO
   ↓ Controller serializes to JSON
GlobalExceptionHandler wraps any thrown ApiException → consistent error JSON
```

### 4.5 Real-time Architecture (Phase 0 result)

- Transport: **WebSocket via STOMP over SockJS fallback**. Endpoint `/ws` upgraded after JWT handshake.
- Topics:
  - `/user/queue/mensagens` — mensagens WhatsApp recebidas para o atendente responsável.
  - `/user/queue/lembretes` — disparos de lembretes (Quartz).
  - `/user/queue/transferencias` — atendimento transferido para o usuário.
  - `/topic/presenca-equipe` — atualizações de presença online da equipe.
  - `/user/queue/notificacoes` — notificações genéricas por usuário.
- Fluxo de fan-out:
  1. Webhook WhatsApp chega em `WhatsappWebhookController`.
  2. Persiste linha em `mensagem` + publica evento RabbitMQ `mensagem.entrada`.
  3. `RealtimeBroadcastService` consome o evento, resolve atendente responsável via `atendimento.atendente_principal_id`, envia frame STOMP para `/user/queue/mensagens`.
  4. Hook frontend `useRealtimeChannel` recebe o frame, atualiza store Zustand, dispara notificação visual + sonora.
- Orçamento de latência vs. NFR-01 (≤ 2s):
  - Meta → webhook: ~100ms
  - Persist + publish: ~50ms
  - Broker hop + STOMP push: ~100ms
  - Browser render + sound: ~200ms
  - **Total p95**: ~450ms — confortavelmente dentro do orçamento de 2s.

---

## 5. Data Layer (preview) — v2 reconciliado com Diagrama

Schema detalhado em [data-model.md](./data-model.md). Resumo:

- **12 entidades do Diagrama de Classes** mapeadas para **12 tabelas principais** em pt-BR + **17 tabelas auxiliares** justificadas (LGPD, audit, multi-tenant). Total **29 tabelas**.
- **Hierarquia Usuario/Gestor/Recepcionista/Medico**: single-table inheritance com `usuario.perfil` como discriminator + tabelas auxiliares `perfil_medico` e `permissoes_recepcionista`.
- **Atendimento ≠ Conversation**: o diagrama define `Atendimento` como **sessão com início e fim**, não thread perpétua. Múltiplos atendimentos por paciente ao longo do tempo (ver `data-model.md` §5.1).
- **Soft delete**: `deletado_em TIMESTAMPTZ NULL` em `paciente`, `usuario`, `tag`, `mensagem_rapida`, `regra_automacao`. Default queries filtram via Hibernate `@SQLRestriction`.
- **Encryption at rest**: column-level AES-256-GCM via `AesGcmConverter` em colunas PHI/PII (nome, cpf, telefone, email, endereço, notas_internas, conteúdo de mensagens, notas clínicas).
- **Auditing**: toda leitura/escrita em tabelas com dado pessoal é registrada via AOP em `log_auditoria`.
- **Multi-tenancy**: todas as tabelas tenant-bearing incluem `clinica_id` mesmo sendo v1 single-tenant — primitives prontas para expansão futura.
- **Reconciliação completa**: ver [data-model-reconciliation.md](./data-model-reconciliation.md) para diff entre v1 (en) e v2 (pt-BR + diagrama).

---

## 6. Security Architecture (LGPD + Constitution P6)

| Concern | Mechanism |
|---------|-----------|
| Authentication | JWT (RS256) access token (15 min TTL) + opaque refresh token (7 day TTL) in HttpOnly+Secure+SameSite=Strict cookie. |
| Authorization | RBAC via `@PreAuthorize`. Capability flags (`canViewDashboard`, `canExportContacts`, `canTransferConversations`) checked at method level. |
| Password hashing | BCrypt cost 12. |
| Sensitive fields | AES-256-GCM at column level. Encryption key in env var (loaded from secrets manager — Vault or AWS Secrets Manager). Key rotation supported via key-id column. |
| Transport | TLS 1.3 (NGINX terminator), HSTS, secure cookies. |
| CORS | Allowlist of `app.clinicafemina.com` origins only. |
| Rate limiting | Bucket4j filter at edge — 60 req/min per IP on `/auth/*`, 600 req/min per user on API. |
| SQL injection | JPA + JPQL with parameterized queries (no string concatenation). |
| Input validation | Bean Validation on every DTO; Zod schemas on every form. |
| Logging | JSON logs via Logback; PII fields excluded via custom converter; correlation ID per request. |
| LGPD audit | All access to `patient`, `consent`, `conversation`, `message`, `appointment` logged in `audit_log` (who, what, when). |
| LGPD consent | `consent` table with legal basis, finality, version, timestamp; UI banner captures it. |
| LGPD export | `LgpdController.exportPatient()` produces CSV + XLSX bundle. |
| LGPD erasure | Soft-delete by default; hard-delete via formal workflow requiring Manager + audit reason. |
| Incident protocol | RabbitMQ topic `lgpd.incident` triggers email to DPO + log to `incident_report` table. |

---

## 7. Integration Architecture

### 7.1 Meta WhatsApp Cloud API

- **Inbound**: Meta calls `POST /api/webhooks/whatsapp` with signed payload. Signature validated via `X-Hub-Signature-256` against shared secret.
- **Outbound**: `WhatsappOutboundService` posts to `https://graph.facebook.com/v20.0/{phone-number-id}/messages` with Bearer token.
- **Media handling**:
  - Inbound media → Meta returns media ID. Service fetches binary via Meta media endpoint, uploads to S3 with KMS-encrypted bucket, persists S3 key in `message.media_url`.
  - Outbound media → reverse: upload to Meta, send message referencing media ID.
- **Retries**: exponential backoff (1s, 2s, 4s, 8s, 16s) on 5xx; circuit breaker via Resilience4j with fallback queuing in RabbitMQ DLX.

### 7.2 N8N Webhook Bus

- **Outbound triggers (system → N8N)**:
  - `lead.new` — new patient first contact
  - `lead.statusChanged` — patient status transition
  - `appointment.scheduled.byHuman` — human-scheduled appointment
  - `appointment.cancelled.byAi` — AI cancellation event
  - `automation.triggered` — automation rule fired
  - `satisfaction.surveySent` — survey dispatched
- **Inbound (N8N → system)**:
  - `POST /api/webhooks/n8n/ai-action` — AI requests system action (schedule, cancel, transfer to human)
  - `POST /api/webhooks/n8n/automation-result` — result of N8N-orchestrated automation
- **Auth**: HMAC-SHA256 signature in `X-Synapse-Signature` header validated against pre-shared secret. Replay protection via `X-Synapse-Timestamp` ±5min window.

### 7.3 Darwin Read-only Sync

- **Pattern**: Pull-based, scheduled via Quartz.
- **Job**: `DarwinSyncJob` runs every 15 min (configurable).
- **Strategy**: incremental delta sync using Darwin's `?updated_after=<lastSyncTs>` filter.
- **Targets imported**: patients, appointments, clinical notes (read-only fields stored in `patient.darwin_imported_data` JSONB column).
- **Conflict resolution**: Darwin is read-only — any conflict with local data marks the patient with `requires_review = true` for manual reconciliation.
- **No outbound**: confirmed by spec FR-INT-04 / RF17.

---

## 8. Real-time & Async Job Architecture

### 8.1 Disparo de Lembrete

```
Quartz cron a cada 1 min
  ↓ LembreteScheduler.encontrarDevidos()
  ↓ Para cada lembrete devido:
      ↓ resolve atendente atual via paciente.atendente_principal_id
      ↓ publica evento RabbitMQ lembrete.devido
      ↓ marca lembrete.disparado_em = NOW()
Consumer RabbitMQ em RealtimeBroadcastService
  ↓ STOMP push para /user/queue/lembretes do atendente
```

Implementa o método `Lembrete.dispararNotificacao()` do Diagrama.

### 8.2 Regras de Automação (48h/24h/2h antes da consulta, cirurgia 72h, satisfação, reativação, feriado)

```
Quartz cron a cada 5 min
  ↓ AutomacaoDispatcher.avaliar()
  ↓ Para cada regra_automacao ativa:
      ↓ query pacientes-alvo do trigger
      ↓ renderiza template_mensagem com variáveis
      ↓ publica em RabbitMQ whatsapp.saida
      ↓ persiste linha em log_automacao
Consumer WhatsappOutboundService
  ↓ envia via Meta Cloud API
  ↓ persiste linha de mensagem outbound
```

### 8.3 Pesquisa de Satisfação

- Disparada quando `agendamento.status` transita para `CONCLUIDO`.
- Quartz job pega após delay configurável (default 4h), dispara prompt WhatsApp.
- Resposta inbound matching o contexto do prompt é parseada (regex 0–10) e persistida em `pesquisa_satisfacao`.

### 8.4 Captura de Cancelamento por IA

- N8N posta webhook `appointment.cancelled.byAi` com `agendamento_id`, `motivo`, `cancelado_em`.
- Service marca `agendamento.cancelado_em`, `cancelado_por_ia = TRUE`, `motivo_cancelamento_ia = <motivo>` (inline conforme diagrama) + persiste linha detalhada em `cancelamento_ia`.

---

## 9. Frontend Real-time + Data Fetching Pattern

- **Initial data**: Server Components fetch via internal service functions calling backend REST. Use `cookies()` to forward auth.
- **Real-time updates**: Client Component wraps page region, opens STOMP connection on mount, subscribes to relevant queue/topic, updates Zustand store on incoming frame.
- **Mutations**: TanStack Query mutations call REST endpoints; on success, optimistic update + invalidate queries.
- **Forms**: react-hook-form + Zod resolver; submission goes through Server Action when feasible or POST to API directly.
- **Loading/Error**: every route segment with async data has `loading.tsx` + `error.tsx` per Constitution P4.

---

## 10. Observability & Compliance

| Layer | Mechanism |
|-------|-----------|
| Application logs | SLF4J → Logback JSON, shipped via Filebeat → ELK / Loki. |
| Correlation ID | Generated by NGINX (`X-Request-Id`), propagated via MDC; included in every log line. |
| Metrics | Micrometer → Prometheus `/actuator/prometheus`. Custom counters: `whatsapp.inbound`, `whatsapp.outbound`, `appointment.created`, `appointment.cancelled.ai`, `auth.failure`. |
| Tracing | OpenTelemetry auto-instrumentation, exported to OTLP collector. |
| Health | `/actuator/health/liveness` + `/actuator/health/readiness`. |
| PII guard | Custom Logback converter strips fields tagged `@Sensitive`. |
| LGPD audit | `audit_log` table; queryable by Manager via LGPD tab. |

---

## 11. Deployment Topology

- **Dev**: Docker Compose (Postgres, RabbitMQ, MinIO, Backend, Frontend, MailHog).
- **Staging**: Single VM with Compose or k3s.
- **Prod**: Kubernetes cluster (3 backend pods minimum behind LoadBalancer), managed Postgres (RDS / Cloud SQL), managed RabbitMQ (CloudAMQP) or self-hosted with HA, S3 bucket with KMS, secrets in Vault/AWS Secrets Manager.
- **CI/CD**: GitHub Actions matrix — lint, unit tests, integration tests (Testcontainers), OWASP dep-check, build container images, push to registry, deploy via Helm.

---

## 12. Constitution Check (Post-Design Gate)

Re-evaluating after Phase 1 design:

| Principle | Status | Verification |
|-----------|--------|--------------|
| P1 Clean Code | ✅ | Layered, single-responsibility services, no god classes. |
| P2 DRY | ✅ | MapStruct mappers, shared `GlobalExceptionHandler`, shared validation schemas. |
| P3 Spring Boot | ✅ | Strict layering enforced in §4.2; `@Transactional` on Service; DTOs at boundary; Flyway. |
| P4 Next.js | ✅ | RSC by default; chat is client-only (necessary for WS + interactivity); `loading.tsx` / `error.tsx` per route. |
| P5 Organization | ✅ | Package layout §4.2 / §4.3. |
| P6 Security | ✅ | JWT + HttpOnly + BCrypt 12 + AES-256-GCM + RBAC + Bean Validation + Zod + Rate limit + CORS allowlist. |
| P7 Testing | ✅ | Testcontainers for integration; ≥80% coverage target on Service + Controller. |
| P8 Observability | ✅ | Structured logs, correlation ID, Micrometer metrics, PII-stripped logs. |

**Gate result**: PASS. Ready for `/speckit-tasks`.

---

## 13. Open Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Meta WhatsApp API rate limits / outages | Outbound queue in RabbitMQ with retry + DLX; surface status to Manager UI. |
| AES key rotation complexity | Key-id column on encrypted fields; rotation job re-encrypts in batches. |
| N8N HMAC secret leak | Rotate every 90 days; alert on signature failure spike. |
| Darwin API schema drift | Strict parsing with version pinning; failure marks last sync, alerts Manager. |
| Real-time scale (>500 concurrent attendants) | Sticky sessions not required (broker-relay handles cross-pod); validate at staging load test. |
| LGPD legal text revisions | `consentimento.versao_texto_consentimento` permite reemissão sem perder histórico. |
| ~~Class diagram from user not received~~ | **RESOLVIDO 2026-05-19**: diagrama recebido e reconciliado — ver [data-model-reconciliation.md](./data-model-reconciliation.md). Modelo v2 alinhado. |

---

## 14. References

- [spec.md](./spec.md) — feature specification
- [research.md](./research.md) — Phase 0 research decisions
- [data-model.md](./data-model.md) — Phase 1 PostgreSQL schema
- [contracts/](./contracts/) — REST, events, webhooks
- [quickstart.md](./quickstart.md) — developer onboarding
- [.specify/memory/constitution.md](../../.specify/memory/constitution.md) — project constitution v1.0.0
