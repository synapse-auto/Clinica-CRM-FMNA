# Tasks: [FEATURE_NAME]

**Plan Ref**: [PLAN_FILE]
**Spec Ref**: [SPEC_FILE]
**Date**: [DATE]
**Assignee**: [ASSIGNEE]

---

## Task Categories

Tasks MUST be categorized using the labels below (from constitution principles):

- `[ARCH]` — Architecture / structure setup (P5)
- `[BE]` — Backend Spring Boot implementation (P3)
- `[FE]` — Frontend Next.js implementation (P4)
- `[REFACTOR]` — DRY / Clean Code improvements (P1, P2)
- `[SEC]` — Security implementation (P6)
- `[TEST]` — Tests (P7)
- `[OBS]` — Observability / logging / metrics (P8)
- `[MIGRATION]` — Database migration scripts (P3)
- `[DOCS]` — Documentation / OpenAPI

---

## Tasks

### Phase 1 — Foundation

- [ ] `[ARCH]` Define package structure for new domain
- [ ] `[MIGRATION]` Create Flyway migration V[N]__[description].sql
- [ ] `[BE]` Create Entity class with JPA annotations
- [ ] `[BE]` Create Request/Response DTOs + Mapper

### Phase 2 — Backend

- [ ] `[BE]` Implement Repository interface (JPA)
- [ ] `[BE]` Implement Service with business logic
- [ ] `[BE]` Implement Controller with @PreAuthorize + OpenAPI docs
- [ ] `[SEC]` Validate tenant isolation in Service
- [ ] `[BE]` Add centralized exception handling if new exceptions

### Phase 3 — Frontend

- [ ] `[FE]` Create Server Component page with data fetching
- [ ] `[FE]` Create Client Components for interactive parts
- [ ] `[FE]` Implement service function (API client)
- [ ] `[FE]` Define Zod schema for form validation
- [ ] `[FE]` Add loading.tsx and error.tsx for route

### Phase 4 — Tests

- [ ] `[TEST]` Unit tests for Service (happy path + edge cases)
- [ ] `[TEST]` Integration tests for Controller endpoints (MockMvc)
- [ ] `[TEST]` Frontend component tests (React Testing Library)
- [ ] `[TEST]` Verify coverage ≥ 80% for new code

### Phase 5 — Observability & Docs

- [ ] `[OBS]` Add structured logging to Service methods (no PII)
- [ ] `[OBS]` Add correlation ID to critical operations
- [ ] `[DOCS]` Verify OpenAPI docs generated correctly

---

## Definition of Done

A task is DONE when:

1. Code follows all 8 constitution principles (no violations)
2. Code reviewed by at least one other contributor
3. Tests pass (unit + integration)
4. Coverage target met (≥ 80% for Service/Controller)
5. No OWASP vulnerabilities introduced
6. OpenAPI docs updated for new endpoints
7. Flyway migration tested on clean schema

---

## Blocked / Deferred

| Task | Blocker | Expected Resolution |
|------|---------|---------------------|
| | | |
