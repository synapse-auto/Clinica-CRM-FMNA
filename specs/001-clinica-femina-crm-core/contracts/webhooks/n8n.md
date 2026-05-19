# Contrato Webhook — Integração N8N

Toda integração N8N usa HMAC-SHA256 + proteção de replay por timestamp.

**Headers** (ambas direções):
- `X-Synapse-Signature: sha256=<hex>` — HMAC de `<timestamp>.<raw_body>` com `N8N_WEBHOOK_SECRET`.
- `X-Synapse-Timestamp: <unix-seconds>` — deve estar dentro de ±5 minutos.
- `X-Synapse-Event: <nome-evento>`
- `X-Request-Id: <uuid>` — correlation id propagado.

---

## Outbound — Sistema → N8N

Backend publica em `${N8N_BASE_URL}/webhook/<nome-evento>`. Cada evento dispara após commit da transação correspondente.

### Evento `lead.novo`
```json
{
  "eventoId": "evt_abc",
  "ocorridoEm": "2026-05-19T14:31:00Z",
  "paciente": { "id": 9876, "nome": "Maria Fernanda Santos", "telefone": "+554498765432" },
  "atendimentoId": 12345,
  "canal": "WHATSAPP"
}
```

### Evento `lead.statusAlterado`
```json
{
  "eventoId": "evt_abc",
  "ocorridoEm": "2026-05-19T14:31:00Z",
  "paciente": { "id": 9876 },
  "statusAnterior": "EM_ATENDIMENTO",
  "statusNovo": "AGENDADO",
  "alteradoPorUsuarioId": 7
}
```

### Evento `agendamento.criadoPorHumano`
```json
{
  "agendamento": {
    "id": 5001,
    "pacienteId": 9876,
    "medicoId": 3,
    "dataHora": "2026-05-20T10:00:00Z",
    "tipoServico": "CONSULTA",
    "origem": "HUMANO"
  }
}
```

### Evento `agendamento.canceladoPorIa`
Evento mirror — sistema grava após confirmação do N8N; não iniciado pelo backend.

### Evento `automacao.disparada`
```json
{
  "tipoRegra": "REMINDER_24H",
  "pacienteId": 9876,
  "agendamentoId": 5001,
  "previaMensagemRenderizada": "Lembramos sua consulta amanhã..."
}
```

### Evento `satisfacao.pesquisaEnviada`
```json
{ "agendamentoId": 5001, "pacienteId": 9876, "enviadaEm": "2026-05-19T18:00:00Z" }
```

---

## Inbound — N8N → Sistema

### POST /api/webhooks/n8n/acao-ia

IA solicita ao sistema que execute uma ação.

**Body**
```json
{
  "acaoId": "act_xyz",
  "acao": "AGENDAR" | "CANCELAR_AGENDAMENTO" | "HANDOFF_HUMANO" | "APLICAR_TAG" | "ATUALIZAR_STATUS_PACIENTE",
  "atendimentoId": 12345,
  "pacienteId": 9876,
  "payload": { ... específico da ação ... }
}
```

**Ação: AGENDAR**
```json
{
  "acaoId": "act_xyz",
  "acao": "AGENDAR",
  "atendimentoId": 12345,
  "pacienteId": 9876,
  "payload": {
    "medicoId": 3,
    "tipoServico": "CONSULTA",
    "dataHora": "2026-05-21T10:00:00Z",
    "dataHoraFim": "2026-05-21T10:30:00Z"
  }
}
```

**Validação server-side (R11 — defense in depth)**:
- Valida assinatura + timestamp.
- Valida que o horário solicitado está dentro da `janela_horario_ia` configurada (ou `clinica.ia_24h = TRUE`).
- Valida que o paciente não está soft-deleted.
- Valida que o médico existe, tem `perfil = 'MEDICO'`, está ativo, e o slot está livre.
- Em qualquer falha: responde `409` com motivo, não cria agendamento. Backend registra em `log_auditoria`.

**Response 201** (sucesso)
```json
{ "agendamentoId": 5001, "status": "AGENDADO" }
```

**Response 409** (regra violada)
```json
{ "error": { "code": "IA_FORA_DA_JANELA", "message": "..." } }
```

**Ação: CANCELAR_AGENDAMENTO**
```json
{
  "acaoId": "act_xyz",
  "acao": "CANCELAR_AGENDAMENTO",
  "payload": {
    "agendamentoId": 5001,
    "motivo": "Paciente pediu para reagendar",
    "codigoMotivo": "PEDIDO_PACIENTE"
  }
}
```

→ Cria linha em `cancelamento_ia` + atualiza `agendamento.cancelado_em`, `cancelado_por_ia = TRUE`, `motivo_cancelamento_ia = <motivo>` (campo inline do Diagrama) + emite evento `agendamento.canceladoPorIa`.

**Ação: HANDOFF_HUMANO**
```json
{
  "acaoId": "act_xyz",
  "acao": "HANDOFF_HUMANO",
  "atendimentoId": 12345,
  "payload": { "atendentePreferidoId": null, "motivo": "Solicitação fora da janela da IA" }
}
```

→ Atualiza `atendimento.tratado_por_ia = FALSE`, atribui ao próximo atendente disponível (round-robin), emite STOMP em `/user/queue/transferencias`.

---

### POST /api/webhooks/n8n/resultado-automacao

Notifica resultado de workflow orquestrado pelo N8N.

```json
{
  "logAutomacaoId": 9012,
  "status": "ENVIADO" | "FALHOU",
  "motivoFalha": "..." 
}
```

---

## Política de Retry

- Outbound para N8N: mesma policy Resilience4j (retry/circuit-breaker) do WhatsApp; em falha final, persistir em `webhook_outbound_dlq` para replay manual.
- Inbound do N8N: idempotência via `acaoId` UUID; `acaoId` duplicado retorna `200` com resultado previamente cacheado.
