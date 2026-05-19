# Quickstart — Clínica Femina CRM (Developer Onboarding)

**Audience**: developers joining the project. After completing this guide, you can run the platform locally and hit a smoke-test endpoint successfully.

---

## 1. Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Docker Desktop | 4.x | docker.com |
| Java (Temurin OpenJDK) | 21 LTS | adoptium.net |
| Node.js | 20 LTS | nodejs.org |
| pnpm | 9.x | `npm i -g pnpm` |
| Git | 2.x | git-scm.com |
| HTTPie or curl | latest | optional |

---

## 2. Repository Layout

```
clinica-femina-crm/
├── backend/                  # Spring Boot service
├── frontend/                 # Next.js app
├── infra/
│   ├── docker-compose.yml    # Postgres, RabbitMQ, MinIO, MailHog
│   └── flyway/               # SQL migrations
├── docs/
└── specs/                    # /speckit artifacts (this folder)
```

---

## 3. Bootstrap Infrastructure

```bash
cd infra
docker compose up -d
```

Services started:
- Postgres on `localhost:5432` (db `clinicafemina`, user `app`, pass `app`).
- RabbitMQ on `localhost:5672` (mgmt UI `localhost:15672`, `guest/guest`).
- MinIO (S3-compatible) on `localhost:9000` (console `localhost:9001`, `minio/minio12345`).
- MailHog on `localhost:8025` for outbound test emails.

Verify:
```bash
docker compose ps
```

---

## 4. Start Backend

```bash
cd backend
cp .env.example .env       # adjust APP_ENCRYPTION_KEY_V1, META_WHATSAPP_*, N8N_WEBHOOK_SECRET, DARWIN_API_TOKEN
./gradlew bootRun
```

Flyway runs migrations on startup. Backend serves on `http://localhost:8080`.

**Health**:
```bash
curl http://localhost:8080/actuator/health
```

**Swagger UI**: `http://localhost:8080/swagger-ui/index.html`.

---

## 5. Start Frontend

```bash
cd frontend
pnpm install
cp .env.local.example .env.local   # set NEXT_PUBLIC_API_BASE_URL, NEXT_PUBLIC_WS_URL
pnpm dev
```

App on `http://localhost:3000`.

Default Manager login (dev only, seeded by `V12__seed_default_clinic_and_admin.sql`):
- Email: `admin@clinicafemina.dev`
- Password: `ChangeMe123!`

**You will be forced to change the password on first login.**

---

## 6. Smoke Tests

### 6.1 Auth round-trip
```bash
http POST localhost:8080/api/auth/login email=admin@clinicafemina.dev password=ChangeMe123!
# → 200 with access token
```

### 6.2 Create a patient
```bash
http POST localhost:8080/api/patients \
  Authorization:"Bearer $TOKEN" \
  fullName="Joana Pereira" \
  phone="+554499998888"
```

### 6.3 WhatsApp inbound simulation
Use the dev tool `scripts/dev/sim-whatsapp-inbound.sh` to POST a Meta-shaped payload to `/api/webhooks/whatsapp` with a signed body. The conversation list in the UI updates in real time via STOMP.

---

## 7. Running Tests

### Backend
```bash
cd backend
./gradlew test                  # unit
./gradlew integrationTest       # Testcontainers (Postgres + RabbitMQ)
./gradlew jacocoTestReport      # coverage
```

Coverage threshold: ≥ 80% on `controller/**` and `service/**` (CI gate per Constitution P7).

### Frontend
```bash
cd frontend
pnpm test                       # Vitest unit
pnpm test:e2e                   # Playwright E2E
```

---

## 8. Coding Standards (quick reference)

- Backend: see [.specify/memory/constitution.md](../../.specify/memory/constitution.md) P3.
- Frontend: see Constitution P4.
- Never field-inject — use constructor DI.
- Never expose JPA entities in responses — use DTOs.
- Never write business logic in Controllers or Next.js API routes.
- Never log PII; tag sensitive fields with `@Sensitive`.

---

## 9. Common Tasks

| Task | Command |
|------|---------|
| Add a Flyway migration | Create `backend/src/main/resources/db/migration/V{N}__description.sql` |
| Generate OpenAPI client for the frontend | `pnpm openapi:gen` (reads from `http://localhost:8080/v3/api-docs`) |
| Rotate AES encryption key | Add new key version to env, run `gradle reEncryptBatch -PfromVersion=v1 -PtoVersion=v2` |
| Run OWASP dependency check | `./gradlew dependencyCheckAnalyze` |
| View Quartz scheduled jobs | `http://localhost:8080/actuator/quartz` |

---

## 10. Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `Flyway migration failed: pg_trgm missing` | Postgres extension not installed | Use the Docker Compose image (extension pre-installed) or `CREATE EXTENSION pg_trgm;` |
| WebSocket connects then closes 4401 | Expired JWT | Frontend should call `/api/auth/refresh` on 4401 then reconnect |
| `Outside working hours` blocking dev login | Default seed schedule blocks late-night logins | Run `scripts/dev/relax-schedule.sh` |
| Meta webhook signature mismatch | Wrong `META_WHATSAPP_APP_SECRET` | Match the App Secret from Meta Developer console |

---

## 11. Next Steps

- Read [spec.md](./spec.md) to understand functional scope.
- Read [plan.md](./plan.md) for architecture.
- Read [data-model.md](./data-model.md) for schema details.
- Browse [contracts/](./contracts/) for API contracts.
- Run `/speckit-tasks` to generate the execution-ready task list.
