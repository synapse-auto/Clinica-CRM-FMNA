<!--
SYNC IMPACT REPORT
==================
Version change: (none) → 1.0.0
Added sections: All (initial creation)
Removed sections: N/A
Modified principles: N/A (initial)
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ created
  - .specify/templates/spec-template.md ✅ created
  - .specify/templates/tasks-template.md ✅ created
Follow-up TODOs:
  - TODO(RATIFICATION_DATE): confirm exact project inception date if different from 2026-05-19
-->

# Project Constitution — Clinica CRM FMNA

**Version**: 1.0.0
**Ratification Date**: 2026-05-19
**Last Amended**: 2026-05-19
**Status**: Active

---

## 1. Purpose

This constitution defines the non-negotiable engineering principles for the Clinica CRM FMNA system. All contributors and AI agents MUST follow these principles. Deviations require an explicit amendment approved via the governance process below.

---

## 2. Principles

### P1 — Clean Code

Code MUST be readable by humans first, machines second.

- Functions and methods MUST do one thing only (Single Responsibility).
- Names MUST be intention-revealing: `findPatientByDocument` not `getData`.
- Functions MUST NOT exceed 30 lines; classes MUST NOT exceed 300 lines.
- Boolean parameters MUST be replaced with named flags or separate methods.
- Dead code MUST be deleted, not commented out.
- Magic numbers MUST be extracted to named constants.

**Rationale**: Unreadable code accumulates technical debt that compounds over time in healthcare systems where correctness is critical.

---

### P2 — DRY (Don't Repeat Yourself)

Every piece of knowledge MUST have a single, authoritative representation.

- Duplicated logic MUST be extracted into shared utilities, services, or hooks.
- Configuration values MUST live in one place (environment files, `application.yml`, or `constants.ts`).
- Common UI patterns MUST be extracted into reusable React components.
- Database query patterns MUST be abstracted into Repository methods.
- Validation rules MUST be centralized (Bean Validation on backend, Zod schemas on frontend).

**Rationale**: Duplication causes divergence — fixing a bug in one copy while missing another is a systemic failure mode.

---

### P3 — Java Spring Boot Best Practices

Spring Boot code MUST follow established layered architecture patterns.

- Architecture layers MUST be: `Controller → Service → Repository → Entity`. Cross-layer jumps (e.g., Controller calling Repository directly) are FORBIDDEN.
- Controllers MUST only handle HTTP concerns (request mapping, response status, DTO conversion). Business logic MUST live in Services.
- Entities MUST NOT be exposed directly in API responses. DTOs (Data Transfer Objects) MUST be used at all boundaries.
- Dependency Injection MUST use constructor injection, not field injection (`@Autowired` on fields is FORBIDDEN).
- `@Transactional` MUST be applied at the Service layer, not Controller or Repository.
- Custom exceptions MUST extend `RuntimeException` and be handled by a centralized `@ControllerAdvice` class.
- All API endpoints MUST be documented with OpenAPI/Swagger annotations.
- Database migrations MUST use Flyway or Liquibase — manual schema changes are FORBIDDEN in production.
- Unit tests MUST cover all Service-layer methods using Mockito. Integration tests MUST cover all Controller endpoints.

**Rationale**: Consistent layering in Spring Boot enables maintainability, testability, and onboarding speed in a domain as complex as clinic management.

---

### P4 — Next.js Best Practices

Next.js code MUST leverage the framework's capabilities correctly.

- Server Components MUST be used by default. Client Components (`'use client'`) MUST only be added when interactivity or browser APIs are required.
- Data fetching MUST occur in Server Components or Route Handlers — never via `useEffect` for initial data.
- API routes MUST be thin: validate input, delegate to a service function, return response. No business logic inline.
- Environment variables MUST follow the `NEXT_PUBLIC_` prefix convention strictly (public vs. private).
- Images MUST use `next/image` for optimization. Raw `<img>` tags are FORBIDDEN except in SVG icons.
- Navigation MUST use `next/link`. Raw `<a>` tags for internal links are FORBIDDEN.
- `loading.tsx` and `error.tsx` MUST be defined for all route segments with async data.
- State management MUST use React Context or Zustand for global state — Redux is FORBIDDEN unless added via a governed amendment.

**Rationale**: Ignoring Next.js conventions results in unnecessary client-side JavaScript, poor Core Web Vitals, and security exposure.

---

### P5 — Code Organization

Project structure MUST be consistent, predictable, and self-documenting.

**Backend (Spring Boot)**:
```
src/main/java/com/synapse/clinicacrm/
  ├── config/          # Spring config, security config, beans
  ├── controller/      # REST controllers
  ├── service/         # Business logic
  ├── repository/      # JPA repositories
  ├── entity/          # JPA entities
  ├── dto/             # Request/Response DTOs
  ├── mapper/          # Entity ↔ DTO mappers
  ├── exception/       # Custom exceptions + ControllerAdvice
  └── util/            # Stateless utility classes
```

**Frontend (Next.js)**:
```
src/
  ├── app/             # Next.js App Router pages and layouts
  ├── components/      # Shared UI components
  │   ├── ui/          # Primitive components (Button, Input, etc.)
  │   └── features/    # Feature-specific composed components
  ├── hooks/           # Custom React hooks
  ├── services/        # API client functions
  ├── store/           # Global state (Zustand stores)
  ├── types/           # TypeScript type definitions and Zod schemas
  └── lib/             # Utilities and helpers
```

- Files MUST be named in `kebab-case` for Next.js and `PascalCase` for classes in Spring Boot.
- Feature folders MUST group related files by domain, not by technical layer when a feature grows large.

**Rationale**: Predictable structure reduces cognitive load. New contributors MUST be able to locate any artifact within 30 seconds.

---

### P6 — Security

Security MUST be built in, not bolted on.

- Authentication MUST use JWT tokens with short expiry (≤ 1 hour). Refresh tokens MUST be stored in `HttpOnly` cookies, never in `localStorage`.
- All endpoints MUST require explicit authorization annotations (`@PreAuthorize`, `@Secured`). No endpoint is public by default except `/auth/**` and `/actuator/health`.
- User input MUST be validated at the API boundary (Spring Validation) and at the UI boundary (Zod). Never trust client data server-side.
- SQL queries MUST use parameterized statements (JPA/JPQL). String concatenation in queries is FORBIDDEN.
- Sensitive data (CPF, passwords, health records) MUST be encrypted at rest. Passwords MUST use BCrypt with cost ≥ 12.
- Secrets (DB credentials, API keys) MUST live in environment variables or a secrets manager — never in source code or committed `.env` files.
- CORS MUST be configured explicitly to allowlisted origins only.
- Dependencies MUST be audited monthly (OWASP Dependency Check for Java, `npm audit` for Node).
- All data access MUST enforce row-level tenant isolation — a user MUST NOT access data from another clinic.

**Rationale**: A clinic CRM handles sensitive personal health information (PHI). A breach causes regulatory liability (LGPD), reputational damage, and patient harm.

---

### P7 — Testing Discipline

Code without tests is assumed broken.

- Every Service method MUST have at least one unit test covering the happy path and one covering failure/edge cases.
- Every REST endpoint MUST have at least one integration test (Spring MockMvc or Testcontainers).
- Frontend components with user interaction MUST have tests using React Testing Library.
- Test names MUST follow the pattern: `should_[expectedBehavior]_when_[condition]`.
- Test doubles (mocks/stubs) MUST NOT be used for the persistence layer in integration tests — use Testcontainers or H2.
- Code coverage MUST NOT drop below 80% for Service and Controller layers.

**Rationale**: Healthcare software errors directly affect patient outcomes. Tests are the primary safety net.

---

### P8 — Observability

Systems MUST be observable in production.

- All service methods MUST log entry/exit at `DEBUG` level and errors at `ERROR` level with structured logging (JSON).
- Log messages MUST NOT contain PII/PHI (patient names, CPF, health data).
- HTTP requests MUST be traced with a correlation ID propagated across service calls.
- Critical operations (appointment creation, payment, auth failure) MUST emit application metrics.
- Health endpoints (`/actuator/health`) MUST expose readiness and liveness probes.

**Rationale**: Unobservable systems are unmaintainable. Healthcare SLAs require rapid incident detection and resolution.

---

## 3. Governance

### Amendment Procedure

1. Author submits a proposed amendment as a PR modifying this file.
2. At least one other contributor reviews and approves.
3. `CONSTITUTION_VERSION` MUST be bumped per semantic versioning (see below).
4. `LAST_AMENDED` date MUST be updated to the merge date.
5. Propagation checklist MUST be completed (see Section 4).

### Versioning Policy

| Change Type | Version Bump |
|-------------|-------------|
| Principle removed or fundamentally redefined | MAJOR |
| New principle or substantial expansion | MINOR |
| Clarification, wording fix, typo | PATCH |

### Compliance Review

- This constitution MUST be reviewed quarterly (every 3 months).
- Any PR that violates a principle MUST be blocked until compliant or an amendment is ratified.
- AI agents operating on this codebase MUST read this constitution before proposing architectural changes.

---

## 4. Propagation Checklist

When amending this constitution, verify alignment in:

- [ ] `.specify/templates/plan-template.md` — architecture decisions reference correct principles
- [ ] `.specify/templates/spec-template.md` — constraint sections reflect current rules
- [ ] `.specify/templates/tasks-template.md` — task types cover principle-driven categories (security, testing, observability)
- [ ] `CLAUDE.md` (if exists) — agent guidance consistent with principles
- [ ] `README.md` — development guidelines section reflects current standards

---

## 5. Non-Negotiables (Quick Reference)

| # | Rule |
|---|------|
| 1 | No dead code — delete it |
| 2 | No duplicated logic — extract it |
| 3 | No field injection — use constructor injection |
| 4 | No entities in API responses — use DTOs |
| 5 | No business logic in Controllers or Next.js API routes |
| 6 | No secrets in source code |
| 7 | No raw SQL string concatenation |
| 8 | No `<img>` or `<a href>` for internal Next.js navigation |
| 9 | No untested Service methods |
| 10 | No PII in logs |
