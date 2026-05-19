# Plan: [FEATURE_NAME]

**Version**: [PLAN_VERSION]
**Date**: [DATE]
**Author**: [AUTHOR]
**Status**: Draft | Under Review | Approved

---

## Constitution Check

Before finalizing this plan, verify compliance with `.specify/memory/constitution.md`:

- [ ] P1 Clean Code — functions/classes within size limits, intention-revealing names
- [ ] P2 DRY — no duplication planned, shared utilities identified
- [ ] P3 Spring Boot — correct layer (Controller/Service/Repository/Entity), constructor DI, DTOs at boundaries
- [ ] P4 Next.js — Server Components by default, no useEffect for data fetching, next/image and next/link used
- [ ] P5 Organization — files placed in correct package/folder per architecture map
- [ ] P6 Security — auth/authz considered, input validation at boundaries, no secrets in code
- [ ] P7 Testing — unit + integration tests planned, coverage target maintained
- [ ] P8 Observability — logging strategy defined, correlation ID propagated

---

## Context

[Why this feature/change is needed. Business driver or technical debt motivation.]

## Goals

- [Goal 1]
- [Goal 2]

## Non-Goals (Out of Scope)

- [What is explicitly NOT being done]

---

## Architecture Decision

### Approach

[Describe the chosen approach and why.]

### Alternatives Considered

| Alternative | Reason Rejected |
|-------------|----------------|
| [Alt 1] | [Reason] |

### Layer Responsibilities (Spring Boot)

| Layer | Responsibility in This Feature |
|-------|-------------------------------|
| Controller | |
| Service | |
| Repository | |
| Entity/DTO | |

### Component Responsibilities (Next.js)

| Component/Page | Type (Server/Client) | Responsibility |
|---------------|---------------------|----------------|
| | | |

---

## Data Model Changes

[Describe any new entities, columns, or migrations. Reference Flyway script names.]

## API Changes

[List new/modified endpoints with method, path, request, response shape.]

## Security Considerations

[Auth requirements, data sensitivity, tenant isolation rules.]

---

## Testing Strategy

| Test Type | What to Cover |
|-----------|--------------|
| Unit (Service) | |
| Integration (Controller) | |
| Frontend (RTL) | |

## Observability

[Log points, metrics, correlation ID strategy.]

---

## Open Questions

- [ ] [Question needing resolution before implementation]

## Dependencies

- [Other features, services, or migrations this depends on]
