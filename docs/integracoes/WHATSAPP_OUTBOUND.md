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

## Providers e checklist

Meta e UAZAP usam o mesmo destinatário resolvido, mas templates são exclusivos da Meta. A resposta
UAZAP atual não confirma destinatário e, portanto, não cria chat ID. Para novo tipo de envio:

- resolver o destinatário central antes do envio;
- enviar apenas uma vez;
- manter `ENVIADA` após aceite com identificador externo;
- aguardar webhook para `ENTREGUE`, `LIDA` ou `FALHA`;
- registrar somente IDs e telefones mascarados nos logs;
- adicionar testes para chat ID confirmado e telefone cadastrado.
