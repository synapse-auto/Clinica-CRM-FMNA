# Contratos de API — Clínica Femina CRM

Diretório com as definições de contratos expostos pela plataforma, alinhados ao Diagrama de Classes UML e ao modelo de dados v2 (pt-BR).

| Subdiretório | Propósito |
|--------------|-----------|
| [rest/](./rest/) | Endpoints REST consumidos pelo frontend Next.js. |
| [events/](./events/) | Tópicos WebSocket / STOMP e payloads. |
| [webhooks/](./webhooks/) | Webhooks inbound + outbound (Meta WhatsApp Cloud API, N8N, Darwin). |

Convenções:
- Payloads em `application/json` salvo notação contrária.
- Timestamps ISO-8601 com timezone (`2026-05-19T14:30:00Z`).
- IDs são `int64` (BIGSERIAL no Postgres) serializados como JSON number.
- Campos de domínio em **pt-BR** (`nome`, `dataHora`, `conteudo`, `atendentePrincipal`, etc.).
- Termos técnicos universais (`accessToken`, `whatsappStatus`, `mimeType`) ficam em inglês.

**Auth**: todo endpoint REST exceto `/auth/login`, `/auth/refresh`, `/webhooks/whatsapp`, e `/webhooks/n8n/*` requer:
- Header `Authorization: Bearer <jwt>` **OU**
- Cookie HttpOnly `access_token` (setado por `/auth/login`).

**Formato de erro** (uniforme):
```json
{
  "error": {
    "code": "REGRA_NEGOCIO_VIOLADA",
    "message": "Status do paciente não pode pular de EM_ATENDIMENTO para FINALIZADO",
    "details": { "de": "EM_ATENDIMENTO", "para": "FINALIZADO" },
    "correlationId": "f1a2b3c4-..."
  }
}
```

## Mapa de Arquivos REST

| Arquivo | Cobre |
|---------|-------|
| [auth.md](./rest/auth.md) | Login, refresh, logout, me |
| [atendimentos.md](./rest/atendimentos.md) | Atendimentos, Mensagens, Transferência (Diagrama: Atendimento + Mensagem) |
| [patients.md](./rest/patients.md) | Pacientes (Diagrama: Paciente) |
| [agenda-and-appointments.md](./rest/agenda-and-appointments.md) | Agenda semanal + Agendamentos (Diagrama: Agendamento) |
| [operational.md](./rest/operational.md) | Lembretes, Tags, Msgs Rápidas, Automação, Horários, Equipe, Dashboard, Satisfação, Cancelamentos IA, Configurações, LGPD |
