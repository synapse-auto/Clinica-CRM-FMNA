# Spec: [FEATURE_NAME]

**Version**: [SPEC_VERSION]
**Date**: [DATE]
**Author**: [AUTHOR]
**Status**: Draft | Review | Approved | Implemented

---

## Summary

[One paragraph description of the feature and its value to the clinic system.]

---

## Requirements

### Functional Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-01 | | Must Have |
| FR-02 | | Should Have |

### Non-Functional Requirements

| ID | Requirement | Constraint Source |
|----|-------------|------------------|
| NFR-01 | All endpoints MUST require authentication | P6 Security |
| NFR-02 | Service methods MUST have unit tests | P7 Testing |
| NFR-03 | No PII in logs | P8 Observability |
| NFR-04 | Input MUST be validated at API boundary | P6 Security |
| NFR-05 | | |

---

## Acceptance Criteria

- [ ] [Specific, testable condition that confirms feature is complete]
- [ ] All new endpoints return appropriate HTTP status codes
- [ ] Unit test coverage ≥ 80% for new Service code
- [ ] No security violations (OWASP checklist passed)
- [ ] Flyway migration script present for schema changes

---

## Constraints

Per project constitution ([.specify/memory/constitution.md](../memory/constitution.md)):

- MUST NOT expose JPA entities directly in API responses (P3)
- MUST use constructor injection (P3)
- MUST validate input with Bean Validation + Zod (P6)
- MUST enforce tenant-level data isolation (P6)
- MUST use Server Components unless interactivity required (P4)

---

## User Stories

### [Story 1 Name]

**As a** [role]
**I want to** [action]
**So that** [benefit]

**Scenarios**:
- Happy path: [description]
- Error case: [description]

---

## UI/UX Notes

[Wireframe references, interaction flows, component library usage (shadcn/ui, etc.)]

---

## Out of Scope

- [Explicitly excluded functionality]

## Open Questions

- [ ] [Unresolved question]
