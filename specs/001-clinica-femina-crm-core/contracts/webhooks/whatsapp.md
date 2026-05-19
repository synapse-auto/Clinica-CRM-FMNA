# Contrato Webhook — Meta WhatsApp Cloud API

**Nota**: Payloads inbound da Meta seguem o formato oficial Meta (campos em inglês). Mapeamento para nossas tabelas (`mensagem`, `paciente`, `atendimento`) acontece em `WhatsappWebhookController` + `WhatsappInboundMapper`.

## GET /api/webhooks/whatsapp/verify

Verification handshake. Meta calls with `hub.mode`, `hub.verify_token`, `hub.challenge`.

**Response**: 200 echoing `hub.challenge` if `hub.verify_token` matches the configured token (env `META_WHATSAPP_VERIFY_TOKEN`). Otherwise 403.

---

## POST /api/webhooks/whatsapp

Inbound message + status callbacks.

**Headers**
- `X-Hub-Signature-256: sha256=<hex>` — HMAC-SHA256 of raw body with `META_WHATSAPP_APP_SECRET`. Verify before processing; reject 401 if mismatch.

**Payload (inbound text)**
```json
{
  "object": "whatsapp_business_account",
  "entry": [{
    "id": "WABA_ID",
    "changes": [{
      "field": "messages",
      "value": {
        "metadata": { "display_phone_number": "554498765000", "phone_number_id": "PNI" },
        "contacts": [{ "profile": { "name": "Maria Fernanda Santos" }, "wa_id": "554498765432" }],
        "messages": [{
          "from": "554498765432",
          "id": "wamid.HBgM...",
          "timestamp": "1747663860",
          "type": "text",
          "text": { "body": "Posso remarcar?" }
        }]
      }
    }]
  }]
}
```

**Processing**
1. Valida assinatura; rejeita se inválida.
2. Persiste payload raw em `whatsapp_payload_log` (replay/debug).
3. Resolve ou cria `paciente` por `wa_id` (E.164 normalizado para `telefone_normalizado`).
4. Resolve `atendimento` ativo (ou cria nova sessão se último foi `ENCERRADO` há > 24h).
5. Persiste linha em `mensagem` (`conteudo` 🔒 encrypted, `remetente = 'PACIENTE'`, `direcao = 'ENTRADA'`).
6. Publica evento RabbitMQ `mensagem.entrada` (consumido por `RealtimeBroadcastService` e `N8nWebhookPublisher`).
7. Retorna `200` em < 5s (requisito Meta).

---

## POST /api/webhooks/whatsapp (status update)

```json
{
  "object": "whatsapp_business_account",
  "entry": [{
    "changes": [{
      "field": "messages",
      "value": {
        "statuses": [{
          "id": "wamid.HBgM...",
          "status": "read",
          "timestamp": "1747663870",
          "recipient_id": "554498765432"
        }]
      }
    }]
  }]
}
```

**Processing**: atualiza `mensagem.whatsapp_status`, `lida_em`/`entregue_em`, emite frame STOMP em `/user/queue/status-mensagem`.

---

## Outbound Send (Backend → Meta)

`POST https://graph.facebook.com/v20.0/{phone-number-id}/messages`

**Headers**: `Authorization: Bearer <META_WHATSAPP_ACCESS_TOKEN>`, `Content-Type: application/json`.

**Body (text)**
```json
{
  "messaging_product": "whatsapp",
  "to": "554498765432",
  "type": "text",
  "text": { "preview_url": false, "body": "Olá, sua consulta foi confirmada." }
}
```

**Body (media)**: `type: "image" | "audio" | "document"` with corresponding object (`{ "id": "<media_id>" }` after media upload).

**Estratégia de retry** (Resilience4j):
- Retries: 5
- Backoff: exponencial, base 1s, max 16s
- Circuit breaker: 50% falha → abre 60s
- Em falha final: persiste em `mensagem.motivo_falha` + RabbitMQ DLX `whatsapp.saida.dlx`.

---

## Outbound Media Upload

`POST https://graph.facebook.com/v20.0/{phone-number-id}/media` multipart.
Receives `{ "id": "<media_id>" }`. Used in subsequent send call.
