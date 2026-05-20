# Feature Specification: Clínica Femina CRM — Core Platform

**Feature ID**: 001-clinica-femina-crm-core
**Status**: Draft
**Created**: 2026-05-19
**Last Updated**: 2026-05-19
**Source Documents**: `Documentacao_Requisitos_ClinicaFemina_CRM 2.pdf` (v1.5), `Inspirações/` (10 MVP screens)

---

## 1. Overview

### 1.1 Summary

The Clínica Femina CRM is a web platform that consolidates patient relationship management for a women's healthcare clinic specialized in prenatal care. The platform unifies WhatsApp-based patient communication, appointment management, automated relationship workflows, and operational analytics into a single workspace, replacing fragmented tools currently used by the manager, receptionists, and doctors.

### 1.2 Business Context

The clinic communicates with patients almost exclusively through WhatsApp, schedules appointments manually, and lacks a centralized view of patient interactions, satisfaction, and team performance. The current process leads to lost leads, missed follow-ups, inconsistent reminder cadence, and no audit trail of who interacted with whom. The CRM must enable the clinic to:

- Serve patients in real time across WhatsApp without losing context.
- Automate the relationship rituals (reminders, post-consultation surveys, reactivations) that drive retention.
- Give the manager a single-pane operational view of patients, team load, and AI-driven scheduling.
- Comply with LGPD (Lei 13.709/2018) for sensitive health data from day one.

### 1.3 Goals

- Centralize 100% of patient WhatsApp conversations inside the CRM with full historical persistence.
- Reduce average response time to inbound patient messages by routing them to the correct attendant with real-time notification.
- Enable the AI agent (via N8N) to autonomously schedule appointments within manager-defined windows, with full traceability of AI actions and cancellations.
- Provide role-based access ensuring sensitive clinical data is only exposed to authorized profiles.
- Meet LGPD requirements for consent, data subject rights, portability, and incident protocol.

### 1.4 Non-Goals (Out of Scope — Current Feature)

- Mobile-first responsive UI (deferred per PT01).
- Automated Follow-Up workflow engine (status label only — full automation deferred per PT02).
- Bidirectional sync with the Darwin system (read-only consumption only — RF17).
- Billing, invoicing, or payment processing.
- Telemedicine / video consultation.
- Architecture, technology stack, database modeling decisions (out of scope by user directive).

---

## 2. User Personas & Roles

### 2.1 Manager (Gestor)

A clinical director or administrator who owns the operation. Needs full visibility into team performance, patient flow, automation rules, AI behavior, and clinic configuration. Approves AI scheduling windows and relationship automation cadence.

### 2.2 Receptionist (Recepcionista)

Front-line attendant handling inbound WhatsApp conversations, scheduling appointments, applying tags, creating reminders, and transferring conversations between attendants. Has access to operational tabs but not to global configuration.

### 2.3 Doctor (Médico)

Clinical professional with restricted, read-mostly access to view patient information relevant to their consultations. Does not handle operational chat triage but is part of the team list and may receive transferred conversations.

### 2.4 Patient (Paciente — External)

Pregnant woman or prospective patient who interacts with the clinic exclusively through WhatsApp. Never logs into the CRM but is the subject of all conversations, appointments, reminders, and surveys.

### 2.5 AI Agent (External Process via N8N)

Automated agent orchestrated through N8N that handles initial triage, scheduling within configured windows, and reminder triggering. Operates under explicit constraints set by the Manager and logs every cancellation it performs.

### 2.6 Darwin System (External — Read-only Data Source)

Specialized clinical CRM whose patient and appointment data is consumed unidirectionally by the platform. Acts as a system of record for clinical-history fields that the platform displays but does not modify.

---

## 3. User Scenarios & Acceptance Tests

### 3.1 Scenario — Real-time Inbound Patient Message

**As a** receptionist on duty
**I want to** be notified instantly when a patient sends a WhatsApp message
**So that** I can respond within the clinic's service-level expectation

**Acceptance criteria**:
- Visual and audible notification appears on the responsible attendant's panel within 2 seconds of the message arriving at the WhatsApp webhook.
- The lead's row in the conversations list updates with an unread indicator.
- Clicking the notification opens the conversation directly to the latest message.
- The right-hand patient context panel populates with phone, email, current attendant, doctor, preferred time, tags, consultation history, last procedure, and active reminders.

### 3.1a Scenario — Filtering the Conversation List

**As a** receptionist or manager
**I want to** narrow the conversation list by responsible attendant and/or by tag
**So that** I can focus on a subset of conversations without losing the All/AI/Human view

**Acceptance criteria**:
- An "atendente" dropdown lists all receptionists plus a default "Todos os atendentes" option (no filter).
- A "tag" dropdown lists all clinic tags plus a default "Todas as tags" option (no filter).
- Each filter is optional: leaving a dropdown at its default applies no constraint for that dimension.
- Active filters combine with AND: attendant filter AND tag filter AND All/AI/Human tab AND free-text search.
- Changing any filter re-queries the list and updates the per-tab counts (Todos / IA / Humano).
- Clearing a filter (selecting the default option) immediately removes that constraint.

### 3.2 Scenario — Sending a Quick Message Template

**As an** attendant in an active conversation
**I want to** insert a pre-saved message by typing `/`
**So that** I can respond consistently without re-typing common content

**Acceptance criteria**:
- Typing `/` in the message field opens a searchable list of the current user's templates only (not other users').
- Templates are grouped by category (Abertura, Orçamento, Agendamento, Suporte, Financeiro, Encerramento, etc.).
- Selecting a template inserts the message text into the input field, editable before sending.
- A user cannot see or edit another user's templates, even if they share the same role.

### 3.3 Scenario — Creating a Patient Reminder

**As an** attendant
**I want to** schedule a reminder linked to a specific patient
**So that** I am notified to follow up at the right time

**Acceptance criteria**:
- A reminder includes patient, date, time, and custom message.
- At the scheduled time, only the attendant currently responsible for the patient receives the notification.
- If the conversation has been transferred to another attendant before the reminder fires, the new attendant inherits the notification.
- Reminders are visible in the patient's context panel and persist across sessions.

### 3.4 Scenario — Transferring a Conversation

**As an** attendant
**I want to** transfer a patient conversation to another attendant
**So that** the right team member can continue the interaction

**Acceptance criteria**:
- Transfer requires an explicit action (not automatic).
- Transfer target can be another receptionist or any doctor.
- The transfer is recorded in the conversation history (who transferred, when, to whom).
- Any active reminders linked to the patient are inherited by the new attendant.
- The previous attendant no longer receives new-message notifications for this patient.

### 3.5 Scenario — AI-driven Appointment Scheduling

**As a** patient
**I want to** request an appointment through WhatsApp at any time
**So that** I get a scheduling response without waiting for human availability

**Acceptance criteria**:
- The AI agent attempts to schedule only within windows configured by the Manager on the Horários tab.
- If the patient's request is outside an AI window, the AI hands the conversation to a human attendant without scheduling.
- A successful AI scheduling creates an appointment record with the patient, doctor, date/time, and source = "AI".
- A failed/refused AI scheduling logs the reason and the conversation is escalated to a human.

### 3.6 Scenario — Patient Kanban View

**As a** manager
**I want to** see all patients grouped by their current status as Kanban columns
**So that** I can spot bottlenecks in the patient journey

**Acceptance criteria**:
- Columns shown: Em Atendimento, Agendado, Follow UP, Finalizado.
- Each card shows patient name, contact, tags, total value, and responsible attendant.
- Status transitions follow the rule: Em Atendimento → Agendado → Finalizado (Follow UP can be applied in parallel at any stage).
- Arbitrarily skipping intermediate statuses is rejected with an error message.
- Toggle between Kanban and List view preserves filters.

### 3.7 Scenario — Applying Multiple Tags to a Patient

**As any** authenticated user
**I want to** assign one or more tags to a patient
**So that** I can categorize and filter patients

**Acceptance criteria**:
- Any user (any profile) can create, rename, recolor, delete, and apply tags.
- Multiple tags can be attached to a single patient.
- Tag listing shows count of associated leads and percentage of total base.
- Deleting a tag prompts confirmation; on confirm, the tag is removed from all patients.

### 3.8 Scenario — Configuring Automated Relationship Rules

**As a** manager
**I want to** enable/disable and customize message rules for each lifecycle moment
**So that** patients receive consistent communication automatically

**Acceptance criteria**:
- Toggleable rules available: consultation reminder 48h before, 24h before, 2h before; surgery confirmation 72h before; post-consultation satisfaction prompt; inactive patient reactivation; annual preventive reminder; national holiday messages.
- Each rule has an editable message body with variable placeholders for patient/doctor/time/clinic.
- Saving updates the active automation immediately (next trigger uses new content).
- Disabling a rule prevents future triggers but does not retract messages already sent.

### 3.9 Scenario — Configuring the AI Scheduling Window

**As a** manager
**I want to** set the weekdays and time ranges in which the AI may schedule appointments
**So that** AI behavior matches clinical capacity

**Acceptance criteria**:
- Selection of weekdays (Mon–Sun) plus start/end time defines the window.
- A "24h AI" toggle bypasses the window restriction.
- Per-attendant working hours are also configured here, controlling when each attendant may access the CRM.
- An attendant attempting access outside their window is blocked and their leads are redirected to available attendants.
- A "Apply default schedule to all" action propagates the AI window settings to all attendants in one click.

### 3.10 Scenario — Post-Consultation Satisfaction Survey

**As a** patient who has finished a consultation
**I want to** receive a satisfaction question on WhatsApp
**So that** the clinic learns about my experience

**Acceptance criteria**:
- After the configured interval following appointment finalization, the system sends a 0–10 rating prompt automatically.
- Reply is captured, stored, and associated with the appointment, patient, and doctor.
- Aggregate metrics (average, distribution, evolution over time) are available to the Manager.
- Non-numeric responses are flagged for human follow-up rather than discarded.

### 3.11 Scenario — Viewing AI Cancellation Summary

**As a** manager
**I want to** review every appointment the AI cancelled
**So that** I can audit AI decisions and tune the automation

**Acceptance criteria**:
- Dedicated tab lists each AI cancellation with patient, doctor, original date/time, cancellation reason identified by AI, and cancellation timestamp.
- Aggregate view shows totals for the period, top reasons, and distribution by doctor and service type.
- Filter by date range, doctor, and reason.
- Manager can drill from an aggregate row to the underlying conversation.

### 3.12 Scenario — Login & Role-based Access

**As a** registered user
**I want to** log in with my individual email and password
**So that** I see only the data and tabs appropriate to my role

**Acceptance criteria**:
- Login form accepts email + password and issues a session token with controlled expiration.
- Shared logins are forbidden — each person has a unique account.
- Manager sees all tabs. Receptionist sees operational tabs (Atendimentos, Dashboard if permitted, Agenda, Pacientes, Equipe read-only, Tags, Msgs Rápidas, Configurações limited to personal preferences). Doctor sees a restricted view focused on their patients and schedule.
- The Manager can grant or revoke attendant access to optional capabilities: viewing Dashboard, exporting contacts, transferring conversations.

### 3.13 Scenario — LGPD Data Subject Request (Export)

**As a** manager processing a patient's data-portability request
**I want to** export the patient's data in a portable format
**So that** the clinic complies with LGPD Article 18

**Acceptance criteria**:
- Manager can export a single patient's records in CSV or XLSX.
- Export includes personal data, contact, conversation history, appointments, tags, reminders, and survey responses.
- Export action is logged with operator identity, target patient, timestamp, and reason.
- Sensitive fields are flagged in the export header (e.g., "dados sensíveis — saúde").

### 3.14 Scenario — Soft Delete of a Patient

**As a** manager
**I want to** remove a patient from active lists without erasing history
**So that** I preserve audit trails while protecting data minimization

**Acceptance criteria**:
- "Delete" on a patient marks them as soft-deleted; they disappear from default lists.
- Conversation history, appointments, and audit trails are preserved.
- A soft-deleted patient does not appear in Kanban, search, or dashboards by default.
- Hard deletion (definitive erasure) requires an explicit, formal request flow — not exposed in standard UI.

### 3.15 Scenario — Dashboard Operational View

**As a** manager
**I want to** see daily/weekly/monthly clinic metrics on one screen
**So that** I can monitor operations at a glance

**Acceptance criteria**:
- Filter by Dia, Semanal, Mensal.
- Cards display: team online count (with breakdown), new patients, total messages, scheduled appointments, pending confirmations, average response time.
- Charts: message peak by hour, patients of the week (new/recurring/scheduled/follow-up), weekly appointments stacked by service type, service distribution donut, loyalty rate.
- Metric deltas vs. prior period are shown with trend arrows.

### 3.16 Scenario — Agenda Weekly View

**As a** manager or receptionist
**I want to** see the week's appointments organized by doctor and day
**So that** I can identify capacity and gaps

**Acceptance criteria**:
- Header cards: today's consultations, weekly total (consultations + exams + surgeries), available doctors, weekly occupancy rate.
- Grid rows by weekday, filterable by "All" or specific doctor.
- Each appointment card shows patient first name and time.
- Side panel breaks down attendance types (Consultas, Exames, Cirurgias, Retornos) with totals.
- Bar chart shows distribution by doctor.

---

## 4. Functional Requirements

### 4.1 Authentication & Access

| ID | Requirement |
|----|-------------|
| FR-AUTH-01 | Users MUST authenticate with individual email + password before accessing any tab. |
| FR-AUTH-02 | Sessions MUST expire after a controlled duration; expired sessions require re-login. |
| FR-AUTH-03 | Three roles are supported with distinct permissions: Manager, Receptionist, Doctor. |
| FR-AUTH-04 | Shared accounts are forbidden — every login MUST be uniquely attributable to one person. |
| FR-AUTH-05 | The Manager MUST be able to enable/disable per-role capabilities: view Dashboard, export contacts, transfer conversations. |
| FR-AUTH-06 | The system MUST block CRM access for an attendant outside their configured working hours and redirect inbound leads to other available attendants. |

### 4.2 Attendance & Chat (Atendimentos)

| ID | Requirement |
|----|-------------|
| FR-CHAT-01 | The platform MUST provide a WhatsApp-style chat interface to communicate with patients. |
| FR-CHAT-02 | The conversation list MUST support filters: All, AI, Human, plus free-text search. |
| FR-CHAT-02a | The conversation list MUST support an optional filter by responsible attendant (receptionist), exposed as a dropdown defaulting to "Todos os atendentes" (no filter applied). |
| FR-CHAT-02b | The conversation list MUST support an optional filter by tag, exposed as a dropdown defaulting to "Todas as tags" (no filter applied). |
| FR-CHAT-02c | The attendant and tag filters MUST be independently activatable: each can be enabled or left at its "all" default, and active filters combine (AND) with the All/AI/Human filter and the free-text search. |
| FR-CHAT-02d | The tag and attendant filter controls MUST be positioned in a filter bar **above the lead list** (top of the left panel of the Atendimentos tab), visible without scrolling the list. Maps to RF23. |
| FR-CHAT-03 | A patient context panel MUST be available on the right side showing phone, email, current attendant, responsible doctor, preferred time, tags, consultation history, last procedure, and active reminders. |
| FR-CHAT-04 | Messages MUST support text, voice notes recorded by the attendant, images, and documents. |
| FR-CHAT-05 | Inbound messages MUST trigger a visual + audible notification on the responsible attendant's screen within 2 seconds of arrival. |
| FR-CHAT-06 | Every message MUST persist permanently with attendant identity and timestamp. |
| FR-CHAT-07 | A conversation MUST have exactly one principal attendant at any time. |
| FR-CHAT-08 | Conversations MUST be transferable to another attendant via explicit action, with the transfer recorded in history. |

### 4.3 Quick Messages (Mensagens Rápidas)

| ID | Requirement |
|----|-------------|
| FR-QMSG-01 | Each user MUST be able to create, edit, and delete their own quick messages. |
| FR-QMSG-02 | Quick messages MUST be categorizable (e.g., Abertura, Agendamento, Orçamento, Suporte, Financeiro, Encerramento, Cirurgia, Orientações). |
| FR-QMSG-03 | Quick messages MUST be private to the creating user — no sharing across users. |
| FR-QMSG-04 | Inside a chat, typing `/` MUST open the current user's quick-message picker. |
| FR-QMSG-05 | The dedicated Msgs Rápidas tab MUST allow filtering templates by category and copying any template to clipboard. |

### 4.4 Reminders (Lembretes)

| ID | Requirement |
|----|-------------|
| FR-REM-01 | An attendant MUST be able to create a reminder linked to a patient with date, time, and custom message. |
| FR-REM-02 | At the scheduled time, the system MUST send a notification only to the patient's current responsible attendant. |
| FR-REM-03 | Reminders MUST persist across attendant transfers — the new attendant inherits all pending reminders for the patient. |
| FR-REM-04 | Reminders MUST be visible in the patient's context panel. |
| FR-REM-05 | Reminders MUST be editable and cancellable before they fire. |

### 4.5 Dashboard

| ID | Requirement |
|----|-------------|
| FR-DASH-01 | Filters MUST allow scope by Day, Week, and Month. |
| FR-DASH-02 | KPI cards MUST display: team online count (with breakdown), new patients, total messages, scheduled appointments, pending confirmations, average response time. |
| FR-DASH-03 | Each KPI MUST show delta vs. previous comparable period with directional indicator. |
| FR-DASH-04 | Charts MUST include: messages-per-hour line, weekly appointment volume by service type, service distribution, patients-of-the-week breakdown (new / recurring / scheduled / follow-up), loyalty rate. |
| FR-DASH-05 | Dashboard data MUST never expose patient PII to users whose role does not have access to that patient's data. |

### 4.6 Agenda

| ID | Requirement |
|----|-------------|
| FR-AGND-01 | Default view MUST be the current week with appointments grouped by weekday and doctor. |
| FR-AGND-02 | Header MUST show: today's consultations, weekly total (consultations + exams + surgeries), available doctors, weekly occupancy rate. |
| FR-AGND-03 | A doctor filter MUST allow viewing All or a specific doctor's agenda. |
| FR-AGND-04 | A side breakdown MUST show counts of Consultations, Exams, Surgeries, Retornos for the week. |
| FR-AGND-05 | A bar chart MUST show appointment distribution per doctor. |

### 4.7 Patients (Pacientes)

| ID | Requirement |
|----|-------------|
| FR-PAT-01 | The Patients tab MUST list all (non-soft-deleted) patients with search by name or phone. |
| FR-PAT-02 | Clicking a patient row MUST navigate the user directly to that patient's conversation in Atendimentos. |
| FR-PAT-03 | Filters MUST allow status (Em Atendimento, Agendado, Follow UP, Finalizado), tag, and other operational dimensions. |
| FR-PAT-04 | Columns MUST display contact, status, tags, total value, and responsible attendant. |
| FR-PAT-05 | Users MUST be able to toggle between List view and Kanban view of the same dataset. |
| FR-PAT-06 | Kanban columns correspond to status values. |
| FR-PAT-07 | Status transitions MUST follow the rule Em Atendimento → Agendado → Finalizado, with Follow UP applicable in parallel; arbitrary status skipping MUST be rejected. |
| FR-PAT-08 | Patients MUST be soft-deletable only — physical deletion requires a separate formal flow not exposed in default UI. |
| FR-PAT-09 | A "New Patient" creation form MUST be available from this tab. |

### 4.8 Team (Equipe)

| ID | Requirement |
|----|-------------|
| FR-TEAM-01 | The tab MUST display all members grouped by role: Gestor, Médicos (with specialty), Recepcionistas. |
| FR-TEAM-02 | Each member card MUST show name, role/specialty, online status, contact (email + phone for manager view). |
| FR-TEAM-03 | Receptionist cards MUST display operational metrics (active conversations, average response time). |
| FR-TEAM-04 | The view MUST be read-only — team CRUD is not in scope of this tab in MVP. |
| FR-TEAM-05 | Doctor specialties supported: Obstetrícia & Pré-natal, Ginecologia, Ultrassonografia, Clínica Geral. |

### 4.9 Automation (Automação)

| ID | Requirement |
|----|-------------|
| FR-AUT-01 | The Automation tab MUST expose toggle + message-body controls for each automated rule. |
| FR-AUT-02 | Required rules in scope: consultation reminder 48h / 24h / 2h before; surgery confirmation 72h before; post-consultation satisfaction prompt; inactive-patient reactivation; annual preventive reminder; national-holiday messages. |
| FR-AUT-03 | Message bodies MUST support variable placeholders (patient name, doctor, time, clinic). |
| FR-AUT-04 | Each rule MUST persist its configuration so future triggers use the latest content. |
| FR-AUT-05 | Disabling a rule MUST stop future triggers without affecting past sends. |

### 4.10 Tags

| ID | Requirement |
|----|-------------|
| FR-TAG-01 | All users MUST be able to create, edit (name + color), delete, and apply tags. |
| FR-TAG-02 | Tag listing MUST display number of associated leads and percentage of total base per tag. |
| FR-TAG-03 | Tags MUST be assignable from Atendimentos and Pacientes tabs. |
| FR-TAG-04 | A patient MUST be able to have multiple tags. |
| FR-TAG-05 | Deleting a tag MUST require confirmation and remove the tag from all associated patients. |

### 4.11 AI Window (Horários)

| ID | Requirement |
|----|-------------|
| FR-HOR-01 | The Manager MUST be able to configure the AI scheduling window by selecting weekdays plus start/end time. |
| FR-HOR-02 | A "24h AI" toggle MUST allow the AI to schedule at any hour. |
| FR-HOR-03 | Each attendant MUST have a configurable working schedule (weekdays + time range). |
| FR-HOR-04 | An attendant attempting to log in outside their schedule MUST be blocked. |
| FR-HOR-05 | Inbound conversations during a blocked attendant's hours MUST be redirected to available attendants. |
| FR-HOR-06 | A "Apply default schedule to all" action MUST propagate the AI window to every attendant in one operation. |
| FR-HOR-07 | The AI MUST refuse to schedule outside the configured window and MUST hand off to a human. |

### 4.12 Settings (Configurações)

| ID | Requirement |
|----|-------------|
| FR-SET-01 | Each user MUST be able to switch between light and dark theme. |
| FR-SET-02 | Notification preferences MUST be configurable per user: new lead, transferred conversation, attendant offline alert. |
| FR-SET-03 | The Manager MUST be able to manage clinic identity: name, contact, visual identity. |
| FR-SET-04 | The Manager MUST configure access toggles: attendants can view Dashboard, attendants can export contacts, attendants can transfer conversations. |
| FR-SET-05 | Saving settings MUST take effect for the next session of affected users. |

### 4.13 Integrations

| ID | Requirement |
|----|-------------|
| FR-INT-01 | The platform MUST integrate with the official Meta WhatsApp Cloud API for inbound + outbound text, audio, image, and document messages. |
| FR-INT-02 | The platform MUST expose webhooks (in/out) to N8N for AI orchestration: new lead, status change, new appointment, AI-triggered events. |
| FR-INT-03 | The platform MUST consume Darwin's API in a read-only manner to import patients, appointments, and clinical information relevant to the CRM. |
| FR-INT-04 | Darwin integration MUST NOT push any data back to Darwin. |
| FR-INT-05 | Integration failures (WhatsApp down, N8N webhook 5xx, Darwin timeout) MUST be retried with backoff and surfaced to the Manager via system status indicator. |

### 4.14 Satisfaction Survey & AI Audit

| ID | Requirement |
|----|-------------|
| FR-SAT-01 | After an appointment is finalized, the system MUST wait a configurable interval and then send a 0–10 satisfaction prompt via WhatsApp. |
| FR-SAT-02 | Responses MUST be stored with link to patient, appointment, and doctor. |
| FR-SAT-03 | The Manager MUST be able to see aggregated satisfaction (average, distribution, time evolution). |
| FR-SAT-04 | A dedicated "AI Cancellations" tab MUST list each cancellation made by the AI: patient, doctor, original date/time, reason identified by AI, cancellation timestamp. |
| FR-SAT-05 | The AI Cancellations view MUST provide aggregates (total in period, top reasons, distribution by doctor and service). |
| FR-SAT-06 | Each cancellation MUST link back to the originating conversation. |

### 4.15 LGPD Compliance (Lei 13.709/2018)

| ID | Requirement |
|----|-------------|
| FR-LGPD-01 | The system MUST capture and store the patient's explicit consent for data collection and communication (consultations, reminders, marketing, loyalty). |
| FR-LGPD-02 | Each consent record MUST include legal basis, finality, timestamp, and a versioned consent text. |
| FR-LGPD-03 | The Manager MUST be able to honor data-subject rights: access, rectification, portability, and erasure. |
| FR-LGPD-04 | Data export for portability MUST be available in CSV and XLSX formats. |
| FR-LGPD-05 | Personal data and sensitive health data MUST be tagged distinctly in storage and exports. |
| FR-LGPD-06 | An incident-notification protocol MUST exist: when an incident is detected, the Manager receives an alert and the system produces a report including affected subjects and data categories. |
| FR-LGPD-07 | All personal-data accesses MUST be logged in an audit trail (who, what, when, why if provided). |
| FR-LGPD-08 | Sensitive fields MUST never appear in logs sent to monitoring or third parties. |

---

## 5. Non-Functional Requirements

| ID | Requirement |
|----|-------------|
| NFR-01 | Inbound WhatsApp messages MUST trigger an in-app notification within 2 seconds of receipt at the webhook. |
| NFR-02 | All communication MUST be over HTTPS (TLS 1.2 or higher). |
| NFR-03 | Passwords MUST be stored hashed with BCrypt at cost ≥ 10. |
| NFR-04 | Session tokens MUST have controlled expiration. |
| NFR-05 | Data at rest MUST be encrypted with AES-256 (or stronger industry-standard). |
| NFR-06 | Page initial load MUST complete in ≤ 3 seconds on a standard connection. |
| NFR-07 | Patient search MUST return results in ≤ 1 second. |
| NFR-08 | Monthly availability MUST be ≥ 99.5%, with planned maintenance announced in advance. |
| NFR-09 | If the platform is built in Java, the OpenJDK distribution MUST be used. |
| NFR-10 | LGPD compliance is a non-negotiable acceptance gate (see FR-LGPD-* for specifics). |

---

## 6. Business Rules

| ID | Rule |
|----|------|
| BR-01 | Patient status transitions follow Em Atendimento → Agendado → Finalizado; Follow UP can be applied in parallel; skipping is rejected. |
| BR-02 | Patient deletion is soft-only by default; hard deletion only via formal flow. |
| BR-03 | The AI may only schedule inside the Manager-configured window; outside-window requests are escalated to humans. |
| BR-04 | Quick messages are strictly per-user — never shared even with same-role users. |
| BR-05 | Every conversation has exactly one principal attendant; transfers are explicit and logged. |
| BR-06 | Reminders fire only to the current responsible attendant for the patient. |
| BR-07 | Tag management (create / rename / delete / apply) is open to all users regardless of role. |
| BR-08 | Reminders survive attendant transfers — the new attendant inherits all pending reminders. |
| BR-09 | Doctors have read-restricted access — they cannot perform receptionist operational actions unless granted explicit transfer access. |
| BR-10 | Backlog items PT01 (mobile responsive) and PT02 (full Follow-Up automation) are explicitly out of scope for this feature. |

---

## 7. Key Entities (Conceptual — No Schema Decisions)

The following entities are mentioned at a conceptual level for shared vocabulary. Modeling decisions (relationships, persistence, identity strategy) are deferred to the planning phase.

- **User** — an authenticated person in the system, bound to a single Role (Manager / Receptionist / Doctor) and a working-hours schedule.
- **Doctor** — specialization of User with a clinical specialty (Obstetrícia & Pré-natal, Ginecologia, Ultrassonografia, Clínica Geral).
- **Patient (Lead)** — external person tracked by the system; has contact info, current status, tags, consent records, and assigned attendant.
- **Conversation** — the ongoing WhatsApp thread tied to one Patient, with one principal Attendant at a time and a transfer history.
- **Message** — a single inbound or outbound WhatsApp item (text, audio, image, document) inside a Conversation, with timestamp and author.
- **Appointment** — scheduled clinical event (consultation, exam, surgery, retorno) tied to a Patient and a Doctor; source = Human or AI.
- **Reminder** — attendant-created prompt tied to a Patient and a moment in time; notifies the current responsible attendant.
- **Quick Message Template** — per-user reusable message text grouped by category.
- **Tag** — global label applicable to Patients, with name, color, and aggregated stats.
- **Automation Rule** — manager-configurable relationship rule (trigger + message body) for specific lifecycle moments.
- **AI Scheduling Window** — manager-defined weekday + time-range constraint dictating when the AI may schedule.
- **Satisfaction Response** — 0–10 score from a Patient about a finalized Appointment.
- **AI Cancellation Record** — log entry capturing every appointment cancellation made by the AI agent.
- **Consent Record** — LGPD-required entry capturing legal basis, finality, and timestamp of a Patient's consent.
- **Audit Log Entry** — record of every access or change to personal data, for LGPD compliance.

---

## 8. Success Criteria

Measurable, technology-agnostic outcomes that define "done" for this feature.

| ID | Criterion | Target |
|----|-----------|--------|
| SC-01 | Time from inbound WhatsApp message to in-app notification on the responsible attendant's screen | ≤ 2 seconds at the 95th percentile |
| SC-02 | Percentage of patient conversations conducted inside the CRM (vs. external WhatsApp) | ≥ 95% within 60 days of go-live |
| SC-03 | Patient search latency | ≤ 1 second at the 95th percentile |
| SC-04 | Initial dashboard load time on standard connection | ≤ 3 seconds |
| SC-05 | Monthly availability | ≥ 99.5% |
| SC-06 | Percentage of AI-attempted scheduling that occurs strictly within Manager-configured window | 100% (zero out-of-window AI scheduling) |
| SC-07 | Percentage of post-consultation patients receiving the satisfaction survey | ≥ 90% within 24h of consultation finalization |
| SC-08 | LGPD data-portability request fulfillment time (Manager export action) | ≤ 5 minutes operator time per patient |
| SC-09 | Reduction in missed appointment confirmations vs. baseline (pre-CRM) | ≥ 30% reduction within 90 days of go-live |
| SC-10 | Number of distinct shared logins detected (audit) | 0 |
| SC-11 | Number of hard-deleted patient records bypassing the formal flow | 0 |
| SC-12 | Audit trail completeness — percentage of personal-data accesses logged | 100% |

---

## 9. Assumptions

- The clinic is initially a single tenant (Clínica Femina). Multi-tenant support is not a requirement of this feature.
- The clinic already has an approved WhatsApp Business account and Meta Cloud API credentials.
- A working N8N instance is reachable from the platform for webhook orchestration.
- Darwin exposes a stable read API and the credentials are managed by clinic IT.
- "Tempo médio de resposta" baseline is captured pre-launch to enable SC-09 measurement.
- Time zone for all scheduling and reporting is America/São_Paulo (BRT/BRST).
- Portuguese (pt-BR) is the only UI language required for v1.
- Audio messages recorded by attendants are stored alongside text messages; no transcription is required in v1.
- Quick-message variable substitution uses a documented placeholder syntax (e.g., `{paciente}`, `{medico}`, `{horario}`, `{clinica}`).
- "Online status" for team members is derived from active session presence within the CRM, not WhatsApp presence.

---

## 10. Dependencies

- **Meta WhatsApp Cloud API** — outbound + inbound messaging, including media support.
- **N8N orchestrator** — webhooks for AI triage, scheduling, reminders, automation triggers.
- **Darwin CRM API** — read-only consumption of patient and appointment data.
- **LGPD legal review** — consent text, finality definitions, and incident protocol require legal sign-off prior to go-live.
- **Clinic staff onboarding** — receptionists, doctors, manager must be trained on the new tool.

---

## 11. Open Clarifications (Resolved with Reasonable Defaults)

The following items had multiple possible interpretations but have been resolved with reasonable defaults documented above. They are listed here for stakeholder visibility and may be revisited during `/speckit-clarify`:

1. **Multi-tenancy**: Defaulted to single-tenant (Clínica Femina). If the platform later serves multiple clinics, isolation rules will need addition.
2. **Satisfaction-survey interval**: Defaulted to configurable per-rule; the default starting value will be set by the Manager in Automação.
3. **Definition of "inactive patient"**: Defaulted to configurable threshold inside the Reativação rule (suggested baseline: 90 days without interaction). To be confirmed with the clinic.

---

## 12. Out of Scope (Explicit Exclusions)

- **PT01** — Mobile-first responsive UI (deferred).
- **PT02** — Full Follow-UP automation workflow (status label only in v1).
- Bidirectional Darwin sync.
- Multi-tenant clinic platform.
- Telemedicine.
- Billing/invoicing/payment processing.
- Architecture, stack, and database decisions (out of scope by user directive — addressed in `/speckit-plan`).
