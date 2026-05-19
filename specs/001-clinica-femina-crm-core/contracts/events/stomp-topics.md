# Contratos WebSocket / STOMP

**Endpoint**: `wss://<host>/ws` (fallback SockJS habilitado).
**Auth**: STOMP CONNECT frame deve incluir `Authorization: Bearer <jwt>`. Conexão rejeitada se JWT inválido ou usuário fora do horário.
**Heartbeat**: cliente `10000,10000`, servidor `10000,10000`.
**Broker**: RabbitMQ STOMP plugin (relay), com prefixos `/topic`, `/queue`, `/user`.

---

## Filas Targeted por Usuário

Frames enviados para `/user/<userId>/queue/...` chegam apenas ao usuário autenticado com identidade correspondente.

### `/user/queue/mensagens`
Mensagem WhatsApp recebida entregue ao atendente responsável.

**Payload**
```json
{
  "tipo": "MENSAGEM_ENTRADA",
  "atendimentoId": 12345,
  "mensagem": {
    "id": 88123,
    "direcao": "ENTRADA",
    "remetente": "PACIENTE",
    "tipoMedia": "TEXTO",
    "conteudo": "Posso remarcar para amanhã?",
    "dataHora": "2026-05-19T14:31:00Z"
  },
  "paciente": { "id": 9876, "nome": "Maria Fernanda Santos" },
  "naoLidas": 3
}
```

### `/user/queue/status-mensagem`
Atualização de status de mensagem outbound previamente enviada.

```json
{
  "tipo": "STATUS_MENSAGEM",
  "mensagemId": 88124,
  "atendimentoId": 12345,
  "status": "LIDA",
  "ocorridoEm": "2026-05-19T14:32:30Z"
}
```

### `/user/queue/lembretes`
Disparo de lembrete (tick do Quartz) — implementa `Lembrete.dispararNotificacao()` do Diagrama.

```json
{
  "tipo": "LEMBRETE_DISPARADO",
  "lembreteId": 401,
  "paciente": { "id": 9876, "nome": "Maria Fernanda Santos" },
  "mensagem": "Confirmar exame de sangue",
  "disparadoEm": "2026-05-20T09:00:00Z"
}
```

### `/user/queue/transferencias`
Atendimento transferido para este usuário.

```json
{
  "tipo": "ATENDIMENTO_TRANSFERIDO",
  "atendimentoId": 12345,
  "de": { "id": 7, "nome": "Ana Lima" },
  "paciente": { "id": 9876, "nome": "Maria Fernanda Santos" },
  "motivo": "Maria pediu para falar com um médico"
}
```

### `/user/queue/notificacoes`
Notificações genéricas (alerta offline, novo lead conforme preferências de Configurações).

```json
{
  "tipo": "NOVO_LEAD",
  "paciente": { "id": 9876, "nome": "Maria Fernanda Santos" },
  "canal": "WHATSAPP"
}
```

---

## Tópicos Públicos

### `/topic/presenca-equipe`
Broadcast quando status online de um membro muda.

```json
{ "usuarioId": 7, "nome": "Ana Lima", "online": true, "ocorridoEm": "2026-05-19T08:00:00Z" }
```

### `/topic/dashboard/{clinicaId}`
Ticks leves para KPIs ao vivo (opcional — frontend pode usar polling como fallback).

```json
{ "codigo": "TOTAL_MENSAGENS_TICK", "valor": 548, "ocorridoEm": "2026-05-19T14:31:00Z" }
```

---

## Destinos Cliente → Servidor

### `/app/digitando`
Indicador de digitação (feature futura; reservado).

```json
{ "atendimentoId": 12345 }
```

### `/app/presenca/ping`
Heartbeat do cliente para manter `online` (a cada 30s).

---

## Falha & Reconexão

- Servidor fecha conexão com código `4401 TOKEN_INVALIDO` se JWT ficar inválido (logout, expiração). Cliente deve chamar `/api/auth/refresh` e reconectar.
- Servidor fecha com `4403 FORA_DO_HORARIO` quando janela de trabalho termina mid-session. Frontend mostra modal bloqueante.
- Cliente usa estratégia STOMP `disconnect`+`reconnect` com backoff jittered (1s, 2s, 5s, 10s, 30s cap).

---

## Orçamento de Latência (NFR-01)

| Etapa | Target |
|-------|--------|
| Meta → webhook | ~100 ms |
| Persist + publish RabbitMQ | ~50 ms |
| Broker relay → STOMP push | ~100 ms |
| Browser receive + render + sound | ~200 ms |
| **End-to-end p95** | **≤ 2 s** confortavelmente |
