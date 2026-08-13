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

## Registro de mensagens interativas enviadas pelo N8N

Quando o workflow envia botões ou lista diretamente ao provider, ele deve registrar a resposta no
callback `/api/n8n/atendimentos/{atendimentoId}/responder` com `enviarWhatsapp=false` e o campo
normalizado `interacao`. Esse campo contém `tipo` (`BOTOES` ou `LISTA`), `textoAcao` e as opções
com `id`, `titulo` e `descricao`. O CRM persiste essa representação cifrada e a devolve pelo
histórico para reconstruir a interface depois de um reload.

Não enviar `interacao` com `enviarWhatsapp=true`: os adapters atuais desse endpoint suportam
texto e mídia, mas não reproduzem a estrutura interativa. O backend rejeita essa combinação para
impedir que o paciente receba somente texto enquanto o CRM registre botões inexistentes.

## Mensagens inbound: Unicode, figurinhas e reações

O envelope inbound comum de Meta e UAZAP preserva `text.body` como Unicode UTF-8, sem
normalização por caractere. Isso mantém pares surrogate, ZWJ, variation selector, modificadores de
tom de pele, bandeiras e keycaps desde o webhook até o DTO do chat. As prévias também são cortadas
por *code point*, nunca por unidade UTF-16.

O tipo inbound `sticker` é classificado canonicamente como `IMAGEM`, preservando o MIME recebido
(por exemplo, `image/webp`). O nome de arquivo genérico `outro` não define o tipo: para esse caso a
apresentação usa `figurinha.webp`. Como defesa para histórico, o frontend renderiza inline toda
mídia com MIME `image/*`, mesmo que o registro antigo esteja como `OUTRO` ou `DOCUMENTO`.

Reações inbound são registradas como o evento textual `Paciente reagiu com {emoji}` e não criam
mídia falsa. Emoji vazio é tratado como remoção e gera o fallback discreto `Paciente removeu uma
reação`; o modelo atual não persiste o alvo da reação, portanto não altera uma bolha existente.
O ID do próprio evento inbound continua sendo a chave de idempotência, sem confundi-lo com o ID da
mensagem reagida.

Respostas `button`, `interactive.button_reply` e `interactive.list_reply` são normalizadas como
mensagens `TEXTO` de entrada. O texto visível tem prioridade sobre `payload`/`id`; respostas de
lista incluem a descrição em uma segunda linha quando presente. O envelope comprovado da UAZAP
também passa pelo modelo inbound comum; não há contrato distinto documentado nesta base para um
formato alternativo de resposta interativa. Tipos fora desse escopo recebem textos amigáveis, como
`Localização recebida`, `Contato compartilhado` ou `Tipo de mensagem ainda não suportado`, sem
placeholders técnicos entre colchetes. Registros históricos não são reescritos.

O envio de figurinhas pelo CRM não é anunciado nem implementado: os adapters atuais enviam WebP
como imagem e não há contrato comprovado, comum e seguro dos dois providers para figurinhas nativas.

## Comando inbound `#reset`

Meta e UAZAP reconhecem o comando no parser inbound canônico somente quando o tipo original é
`text` e `text.body`, após `trim`, é exatamente `#reset` sem diferença entre maiúsculas e
minúsculas. Legendas de mídia, reações, botões, listas e textos que apenas contêm essa sequência
não acionam o comando. O conteúdo original, inclusive espaços e caixa, continua persistido e
visível no histórico.

Depois de persistir a mensagem e antes de decidir o encaminhamento ao N8N, o atendimento ativo é
bloqueado por clínica e passa atomicamente para IA: `tratadoPorIa=true`,
`atendentePrincipal=null`, `humanoDesde=null` e `status=ATIVO`. A mesma mensagem segue pelo evento
N8N já existente, executado depois do commit. Se o atendimento já estiver em IA, o estado não é
salvo novamente, mas uma nova mensagem com outro `whatsappMessageId` continua chegando ao N8N.
O retry do mesmo ID é descartado pela deduplicação anterior à detecção do comando.

Clínicas sem N8N ainda concluem a mudança para IA; o listener existente ignora o envio externo de
forma segura. Um atendimento encerrado por corrida não é reaberto pelo comando. A atualização de
modo é propagada após o commit pelo tópico existente do dashboard e o polling atual do CRM também
revalida lista, detalhe e histórico, mantendo a bolha `#reset`.
