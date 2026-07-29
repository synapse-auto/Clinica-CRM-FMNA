# Endpoints N8N

Os endpoints `/api/n8n/**` são integrações servidor a servidor. Eles não usam
JWT de usuário e exigem o segredo configurado no backend.

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
