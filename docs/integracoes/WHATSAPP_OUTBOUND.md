# WhatsApp outbound

## Status da mensagem

O aceite do provider não comprova entrega. O fluxo é `PENDENTE` → `ENVIADA` → `ENTREGUE` →
`LIDA`; uma rejeição confirmada usa `FALHA`. Webhooks são a fonte de verdade para entrega,
leitura e falha. O motivo de falha é sanitizado e nunca inclui payload, telefone, token ou texto.

## Destinatário único

Todo texto e template resolve o destinatário no `WhatsappRecipientService`: primeiro
`atendimento.whatsappChatId` confirmado por inbound/provider; na ausência, usa o telefone
cadastrado do paciente. Nunca tentar aliases alternativos ou enviar para mais de um número.
Uma resposta do provider só atualiza `whatsappChatId` quando informar uma identidade válida e
compatível com os aliases seguros do paciente.

## Contrato efetivo da FMNA/Uzapi

A FMNA usa **Uzapi/Autotic**, versão de API configurada como `v1`. O contrato oficial é
`POST {baseUrl}/{username}/{version}/{phone_number_id}/messages`, com
`Authorization: Bearer <token>` e `Content-Type: application/json`. Os valores de host, usuário,
instância e token ficam exclusivamente nas variáveis `UAZAP_*` e não devem ser registrados.

Para texto, o body contém `to` somente com dígitos, `delayMessage: 0`, `delayTyping: 0`,
`type: "text"` e `text.body`. Os delays são opcionais no Swagger; a FMNA os envia explicitamente
com zero, conforme o exemplo oficial.

O aceite só é válido quando a resposta HTTP é 2xx, `status` é `success`, `queueId` e `messageId`
internos estão presentes e `messages[0].id` contém o `wamid`. O CRM persiste **somente o `wamid`**
como `whatsappMessageId`; `queueId` e `messageId` interno são usados apenas em logs mascarados.
Se houver `error`, status diferente de `success` ou ausência do `wamid`, a mensagem fica em `FALHA`.

Quando `contacts[0].wa_id` vier na resposta, ele é aceito como identidade confirmada apenas após
pertencer aos aliases seguros do paciente. Assim, a Uzapi pode resolver a variante brasileira
correta no primeiro envio sem tentativa adicional nem segundo destinatário.

Os webhooks Uzapi usam o envelope `entry[].changes[].value`. Para status, a correlação é feita por
`statuses[].id`, que deve corresponder ao `wamid` persistido; `delivered`, `read` e `failed`
atualizam respectivamente `ENTREGUE`, `LIDA` e `FALHA`. O Swagger também expõe eventos de conexão
para diagnosticar instância conectada ou desconectada.

Fontes oficiais: [Teste de Endpoints Uzapi](https://uzapi.com.br/docs/api/teste-de-endpoints/) e
[Swagger Uzapi](https://api.uzapi.com.br/swagger).

## Providers e checklist

Meta e Uzapi usam o mesmo resolvedor central de destinatário, mas templates são exclusivos da Meta.
Para novo tipo de envio:

- resolver o destinatário central antes do envio;
- enviar apenas uma vez;
- persistir `ENVIADA` apenas após aceite comprovado pelo contrato do provider;
- aguardar webhook para `ENTREGUE`, `LIDA` ou `FALHA`;
- registrar somente IDs e telefones mascarados nos logs;
- adicionar testes para chat ID confirmado e telefone cadastrado.
