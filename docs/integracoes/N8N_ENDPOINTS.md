# Endpoints N8N

Os endpoints `/api/n8n/**` são integrações servidor a servidor. Eles não usam
JWT de usuário e exigem o segredo configurado no backend.

## Registrar resposta da IA no atendimento

`POST /api/n8n/atendimentos/{atendimentoId}/responder`

O callback aceita mensagens de texto comuns e, opcionalmente, a representação normalizada
de uma mensagem interativa já enviada pelo workflow ao WhatsApp. O CRM não recebe payload bruto
da Meta: recebe apenas os dados necessários para persistir e reconstruir o histórico.

### Exemplo com botões

```json
{
  "pacienteId": 20,
  "mensagem": "O que você gostaria?",
  "tipoMedia": "TEXTO",
  "origem": "N8N",
  "enviarWhatsapp": false,
  "whatsappMessageId": "wamid.EXEMPLO",
  "enviadoEm": "2026-08-13T15:00:00Z",
  "interacao": {
    "tipo": "BOTOES",
    "textoAcao": "Escolher opção",
    "opcoes": [
      { "id": "agendar", "titulo": "Agendar consulta", "descricao": null },
      { "id": "atendente", "titulo": "Falar com atendente", "descricao": null }
    ]
  }
}
```

`interacao` é opcional. `tipo` aceita `BOTOES` (de 1 a 3 opções) ou `LISTA`
(de 1 a 10 opções). Cada opção preserva seu `id` para correlação interna, mas o CRM
apresenta `titulo` e `descricao` aos usuários.

O workflow continua responsável por enviar a estrutura interativa ao provider. Depois do aceite,
deve registrar no callback o mesmo texto, as opções normalizadas e o ID externo, usando
`enviarWhatsapp=false`. O backend rejeita `interacao` com `enviarWhatsapp=true`, pois os adapters
atuais do CRM enviam somente texto nesse endpoint e isso criaria divergência com o WhatsApp.

Mensagens comuns permanecem compatíveis sem o campo `interacao`. Respostas inbound
`button_reply` e `list_reply` são normalizadas pelo webhook e persistidas com o título visível
escolhido pelo paciente, nunca apenas com o ID interno.

## Transferir para o próximo humano

`POST /api/n8n/atendimentos/{atendimentoId}/transferir-proximo-humano`

### Headers obrigatórios

```http
Content-Type: application/json
X-N8N-SECRET: <N8N_CALLBACK_SECRET>
Idempotency-Key: <chave-unica-da-operacao>
```

A `Idempotency-Key` deve ter entre 1 e 120 caracteres. Um retry da mesma
operação deve repetir a mesma chave. A chave não pode ser reutilizada em outro
atendimento nem com uma lista, ordem, motivo ou resumo diferentes.

### Body oficial

```json
{
  "atendentesIds": [10, 11],
  "motivo": "Transferência por rodízio"
}
```

A ordem de `atendentesIds` define o rodízio. São aceitos de 1 a 20 IDs únicos e
positivos. O endpoint também aceita números enviados como strings e o array
serializado como texto:

```json
{
  "atendentesIds": ["10", "11"],
  "motivo": "Transferência por rodízio"
}
```

```json
{
  "atendentesIds": "[10,11]",
  "motivo": "Transferência por rodízio"
}
```

Aliases aceitos: `atendentes_ids`, `atendenteIds` e `idsAtendentes`.

### Resposta 200

```json
{
  "atendimentoId": 30,
  "modo": "HUMANO",
  "transferido": true,
  "novoAtendenteId": 11,
  "posicaoSelecionada": 1,
  "modoSelecao": "RODIZIO"
}
```

Um retry idêntico retorna `200` com a transferência já registrada, sem criar
nova transferência, evento, notificação ou broadcast.

### Erros

| HTTP | `code` | Situação |
|---|---|---|
| 400 | `INVALID_TRANSFER_PAYLOAD` | Lista, alias ou `Idempotency-Key` inválidos |
| 400 | `INVALID_JSON` | Corpo não é JSON válido |
| 401/403 | conforme autenticação | `X-N8N-SECRET` ausente ou inválido |
| 404 | conforme recurso | Atendimento ou atendente não encontrado na clínica |
| 409 | `IDEMPOTENCY_CONFLICT` | Chave reutilizada em outra operação ou com payload diferente |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | `Content-Type` diferente de `application/json` |

Exemplo sem o header de idempotência:

```json
{
  "status": 400,
  "code": "INVALID_TRANSFER_PAYLOAD",
  "message": "Payload de transferência inválido.",
  "details": {
    "idempotencyKey": "Informe o header Idempotency-Key."
  }
}
```

### cURL

```bash
curl -X POST \
  "https://<backend>/api/n8n/atendimentos/30/transferir-proximo-humano" \
  -H "Content-Type: application/json" \
  -H "X-N8N-SECRET: <segredo>" \
  -H "Idempotency-Key: transferencia-atendimento-30-evento-123" \
  --data '{"atendentesIds":[10,11],"motivo":"Transferência por rodízio"}'
```

Para repetir a requisição após timeout, use exatamente a mesma
`Idempotency-Key` e o mesmo body. Para uma nova transferência, gere outra
chave.

### Postman

1. Selecione `POST` e informe a URL do endpoint.
2. Em **Headers**, adicione os três headers obrigatórios.
3. Em **Body**, selecione **raw** e **JSON**.
4. Cole o body oficial e envie uma única vez.
5. Em um retry técnico, não gere uma chave nova.
