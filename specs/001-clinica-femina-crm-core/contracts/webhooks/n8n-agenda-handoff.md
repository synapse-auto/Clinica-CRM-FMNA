# Especificação de Integração — Agenda N8N (handoff)

Documento de handoff para o desenvolvedor do workflow N8N ("Dylan") integrar-se à
Agenda provider-agnostic do CRM (`/api/n8n/agenda/**`). Reflete exatamente o que
está implementado em `N8nAgendaController` — não é um documento aspiracional.

**Este documento NÃO contém o valor real de `N8N_CALLBACK_SECRET`.** O segredo é
específico de cada ambiente/deployment e deve ser obtido separadamente, por canal
seguro, com quem administra o ambiente. Este documento também não altera o workflow
N8N existente — é apenas a referência de contrato para quem for adaptá-lo.

## 1. Arquitetura relevante para o workflow

- **Um deployment do backend atende exatamente uma clínica** (resolvida
  server-side via `CLINIC_SLUG`). O workflow **nunca** informa `clinicaId` em
  nenhuma requisição — não existe esse campo em nenhum endpoint abaixo, e enviar
  um campo extra com esse nome é ignorado (o backend sempre resolve a clínica do
  próprio deployment).
- O backend decide sozinho se a clínica usa Medware ou Darwin por trás da Agenda.
  O workflow **nunca** precisa saber qual integração está ativa — os campos são
  sempre os normalizados abaixo, independente do provider.
- Consulte `GET /api/n8n/agenda/profissionais` (ou `GET /api/agenda/capabilities`,
  autenticado por sessão de usuário, não por N8N) para saber previamente se a
  clínica atual suporta catálogo de profissionais/horários. Uma clínica Medware
  responde `501` nesse endpoint — trate como "catálogo indisponível", não como erro
  fatal do workflow.

## 2. Autenticação

Todas as rotas abaixo exigem o header:

```
X-N8N-SECRET: <segredo do ambiente — obter fora deste documento>
```

- Header ausente, vazio, ou com valor incorreto → **401 Unauthorized**.
- Segredo não configurado no ambiente (erro de operação, não do workflow) → também
  **401 Unauthorized**.
- Segredo correto, mas a clínica do deployment tem a integração N8N desabilitada
  (`clinica.usa_n8n = false`) → **403 Forbidden**.

A comparação do segredo é feita em tempo constante (`MessageDigest.isEqual`) — não
há diferença de latência observável entre "quase certo" e "totalmente errado".

## 3. Idempotência (criação de agendamento e encaixe)

`POST /api/n8n/agenda` e `POST /api/n8n/agenda/encaixe` exigem o header:

```
Idempotency-Key: <string opaca gerada pelo workflow, única por tentativa lógica>
```

- **Ausente ou em branco** → `400 Bad Request` (`"Cabeçalho Idempotency-Key é
  obrigatório."`).
- **Mesma chave + mesmo corpo da requisição**, reenviada (retry por timeout, por
  exemplo) → devolve o **mesmo resultado** da primeira execução, sem duplicar o
  agendamento (mesmo `idLocal`).
- **Mesma chave + corpo diferente** → `409 Conflict`
  (`code: "IDEMPOTENCY_KEY_PAYLOAD_CONFLITANTE"`). Gere uma chave nova para uma
  operação logicamente diferente.
- **Chave diferente** → sempre tratada como uma nova operação, mesmo com o mesmo
  paciente/horário (dois profissionais no mesmo horário, ou uma consulta normal e
  um encaixe no mesmo horário, são operações distintas e **não** colidem).
- A chave é isolada por clínica e por tipo de operação (`CRIAR_AGENDAMENTO` vs
  `CRIAR_ENCAIXE` são namespaces diferentes) — a mesma chave pode, em tese, ser
  reaproveitada para operações de tipos diferentes sem conflito, mas **não é
  recomendado**: gere uma chave nova por tentativa lógica de negócio.
- Recomendação prática: `idempotencyKey = <atendimentoId ou conversationId>:<intent-id-do-workflow>`.

## 4. Envelope de erro padrão

Todo erro (exceto 2xx) responde neste formato:

```json
{
  "timestamp": "2026-07-25T14:31:00.123-03:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Descrição legível do erro.",
  "code": "CODIGO_OPCIONAL_MAQUINA",
  "path": "/api/n8n/agenda",
  "details": { "campo": "mensagem de validação" }
}
```

`code` e `details` só aparecem quando aplicável. Trate `message` como texto para
log/depuração, não para lógica condicional do workflow — use `status` e, quando
presente, `code`.

## 5. Tabela de status HTTP

| Status | Quando ocorre |
|---|---|
| 200 | GET com sucesso; `PUT` (reagendamento) com sucesso |
| 201 | `POST` de criação (paciente, agendamento, encaixe) com sucesso |
| 204 | `DELETE` (cancelamento) com sucesso — sem corpo |
| 400 | Corpo inválido/JSON malformado; parâmetro obrigatório ausente; `Idempotency-Key` ausente; CPF inválido; erro de validação de campos |
| 401 | `X-N8N-SECRET` ausente, incorreto, ou não configurado no ambiente |
| 403 | Secret correto, mas integração N8N desabilitada para a clínica |
| 404 | Paciente/agendamento não encontrado **na clínica do deployment atual** (nunca vaza dado de outra clínica) |
| 409 | `Idempotency-Key` reutilizada com payload diferente; conflito de escrita na Darwin (ex.: horário ocupado) |
| 429 | A integração Darwin (upstream) sinalizou limite de requisições excedido — reenvie mais tarde com backoff |
| 502 | Resposta inválida/inesperada da integração Darwin (upstream) |
| 504 | Timeout ao comunicar com a integração Darwin (upstream) |
| 501 | Operação não suportada pelo provider da clínica atual (ex.: catálogo de profissionais numa clínica Medware) |

429/502/504 só ocorrem em clínicas Darwin (Medware não tem chamada de rede
externa síncrona nesse fluxo) e refletem o estado momentâneo da Darwin, não um
bug do workflow — implemente retry com backoff exponencial nesses três casos,
reaproveitando a mesma `Idempotency-Key` para chamadas de criação.

## 6. Endpoints

Base URL: `https://<host-do-deployment-da-clinica>` (uma clínica por deployment).

### 6.1 `GET /api/n8n/agenda/profissionais`

Lista os profissionais da clínica.

**Headers**: `X-N8N-SECRET`

**Response 200**
```json
[{ "id": "101", "nome": "Dra. Renata", "origem": "DARWIN" }]
```

**Erros**: 401, 403, 501 (provider sem catálogo — ex.: Medware).

### 6.2 `GET /api/n8n/agenda/horarios?date=YYYY-MM-DD&professionalId=101&professionalId=102`

Lista horários disponíveis na data informada, opcionalmente filtrando por um ou
mais `professionalId` (parâmetro repetível).

**Headers**: `X-N8N-SECRET`

**Response 200**
```json
[{
  "timetableId": "tt-1", "profissionalId": "101", "profissionalNome": "Dra. Renata",
  "localId": "loc-1", "localNome": "Unidade Centro",
  "data": "2026-07-20", "horarioInicio": "09:00", "horarioFim": "09:30"
}]
```

**Erros**: 400 (`date` ausente/inválido — use `YYYY-MM-DD`), 401, 403, 501.

### 6.3 `GET /api/n8n/agenda/paciente?cpf=11144477735`

Lista agendamentos de um paciente pelo CPF (apenas dígitos).

**Headers**: `X-N8N-SECRET`

**Response 200**: array de `AgendaAgendamentoDTO` (ver 6.5). Lista vazia se o CPF
não tiver agendamentos **ou** estiver fora do escopo conhecido pela Darwin
(`coverage: "KNOWN_CRM_PATIENTS_ONLY"` — Darwin só enxerga pacientes já
conhecidos pelo CRM).

**Erros**: 400 (CPF inválido), 401, 403.

### 6.4 `POST /api/n8n/agenda/pacientes`

Cria o paciente ou localiza um já existente pelo CPF (idempotente por natureza —
**não** exige `Idempotency-Key`).

**Headers**: `X-N8N-SECRET`

**Body** (`NovoPacienteRequest`)
```json
{
  "cpf": "11144477735",
  "nome": "Maria da Silva",
  "telefone": "+5511988887777",
  "email": null,
  "dataNascimento": "1990-05-10"
}
```
Campos obrigatórios na prática: `cpf`, `nome`. `telefone`, `email`,
`dataNascimento` são opcionais (envie `null` quando não houver o dado).

**Response 201**
```json
{ "id": 20, "nome": "Maria da Silva", "cpfMascarado": "***.***.777-35" }
```

**Erros**: 400 (CPF inválido/corpo malformado), 401, 403, 502/504 (upstream Darwin).

### 6.5 `POST /api/n8n/agenda`

Cria um agendamento normal.

**Headers**: `X-N8N-SECRET`, `Idempotency-Key` (ver seção 3)

**Body** (`NovoAgendamentoRequest`)
```json
{
  "pacienteId": 20,
  "pacienteCpf": null,
  "profissionalId": "101",
  "localId": "loc-1",
  "timetableId": "tt-1",
  "data": "2026-07-20",
  "horarioInicio": "09:00",
  "horarioFim": "09:30",
  "procedimentoId": "proc-1",
  "procedimentoNome": "Consulta pré-natal",
  "convenioId": "ins-1",
  "observacao": "Encaminhado pelo pronto atendimento"
}
```
- `pacienteId` **ou** `pacienteCpf` deve identificar um paciente já
  criado/localizado via 6.4 — envie o que tiver disponível; prefira `pacienteId`.
- `timetableId` deve vir de um horário retornado por 6.2 (garante que o slot
  ainda está livre no momento da consulta). Sem `timetableId` a Darwin pode
  recusar com `400`/`409` dependendo da disponibilidade real.
- Campos não anotados como obrigatórios podem ser `null`, mas quanto mais
  completos, menor a chance de rejeição pela Darwin (o backend não valida
  antecipadamente todos os campos — a validação final é feita pela Darwin e
  repassada como 400/409/502).

**Response 201** (`AgendaAgendamentoDTO`)
```json
{
  "idLocal": 501, "externalId": "sch-501", "provider": "DARWIN",
  "pacienteId": 20, "pacienteNome": "Maria da Silva", "pacienteCpfMascarado": "***.***.777-35",
  "profissionalId": "101", "profissionalNome": "Dra. Renata",
  "procedimentoId": "proc-1", "procedimentoNome": "Consulta pré-natal",
  "convenioId": "ins-1", "convenioNome": "Particular",
  "localId": "loc-1", "localNome": "Unidade Centro",
  "data": "2026-07-20", "horarioInicio": "09:00", "horarioFim": "09:30",
  "status": "AGENDADO", "timetableId": "tt-1", "observacao": "...",
  "origem": "INTEGRACAO_EXTERNA", "lastSyncedAt": "2026-07-20T09:00:05-03:00",
  "syncStatus": "SYNCED"
}
```
`syncStatus` pode vir `"PENDING_RECONCILIATION"` quando a Darwin confirmou a
gravação mas a consulta de confirmação ainda não encontrou o registro espelhado
— **isso não é um erro**: o agendamento foi criado, apenas aguarde e trate como
sucesso (o CRM reconcilia automaticamente depois).

**Erros**: 400, 401, 403, 404 (paciente não encontrado na clínica), 409 (chave
repetida com payload diferente, ou conflito de horário na Darwin), 429/502/504.

### 6.6 `POST /api/n8n/agenda/encaixe`

Idêntico a 6.5, mas para encaixes (pode sobrepor horários existentes). Mesmo
corpo (`NovoAgendamentoRequest`), mesma resposta (`AgendaAgendamentoDTO`), mesmas
regras de `Idempotency-Key` — **em um namespace de idempotência separado** de
6.5 (a mesma chave usada em 6.5 não colide com uma chamada em 6.6).

### 6.7 `PUT /api/n8n/agenda/{id}`

Reagenda um agendamento existente (`id` = `idLocal` retornado em 6.5/6.6/6.3).

**Headers**: `X-N8N-SECRET`

**Body** (`AtualizarAgendamentoRequest`, todos os campos opcionais — envie apenas
o que muda)
```json
{
  "status": null,
  "data": "2026-07-22",
  "horarioInicio": "10:00",
  "horarioFim": "10:30",
  "timetableId": "tt-9",
  "procedimentoId": null,
  "convenioId": null,
  "observacao": "Reagendado a pedido da paciente"
}
```

**Response 200**: `AgendaAgendamentoDTO` atualizado.

**Erros**: 400, 401, 403, 404 (`id` não encontrado **na clínica do deployment
atual** — não existe forma de reagendar algo de outra clínica), 409, 429/502/504.

### 6.8 `DELETE /api/n8n/agenda/{id}?motivo=texto-livre`

Cancela um agendamento. `motivo` é opcional (default:
`"Cancelado via automação"`).

**Headers**: `X-N8N-SECRET`

**Response 204**: sem corpo.

**Erros**: 401, 403, 404 (mesma regra de escopo por clínica de 6.7), 409, 429/502/504.

## 7. Fluxos de ponta a ponta

**Agendar consulta nova (fluxo normal)**
1. `GET /profissionais` → escolher `profissionalId`.
2. `GET /horarios?date=...&professionalId=...` → escolher `timetableId`.
3. `POST /pacientes` → obter `pacienteId` (cria ou localiza pelo CPF).
4. `POST /agenda` com `Idempotency-Key` própria da tentativa → `idLocal`.

**Consultar agenda de um paciente**
- `GET /paciente?cpf=...` a qualquer momento, sem depender dos passos acima.

**Encaixe (sem respeitar grade de horários)**
1-3. iguais ao fluxo normal.
4. `POST /agenda/encaixe` com `localId` direto (sem `timetableId` de catálogo) e
   `Idempotency-Key` própria.

**Reagendar**
1. Ter o `idLocal` (retornado na criação ou via `GET /paciente`).
2. Opcionalmente `GET /horarios` de novo para achar um `timetableId` livre.
3. `PUT /agenda/{id}` com os campos que mudam.

**Cancelar**
1. Ter o `idLocal`.
2. `DELETE /agenda/{id}?motivo=...`.

## 8. Testes de contrato

`backend/src/test/java/com/synapse/clinicafemina/controller/N8nAgendaControllerTest.java`
simula exatamente as requisições documentadas acima (headers, corpos, status
codes, comportamento de idempotência, isolamento entre clínicas) contra o
controller real, com o provider mockado — é a fonte de verdade executável deste
contrato. Rode com:

```
./gradlew test --tests "com.synapse.clinicafemina.controller.N8nAgendaControllerTest"
```

## 9. Fora de escopo deste documento

- Não altera o workflow N8N existente — é referência para quem for adaptá-lo.
- Não inclui o valor real de `N8N_CALLBACK_SECRET` nem de nenhum outro segredo.
- Não cobre `/api/n8n/atendimentos/**` (contrato de conversa/mensageria,
  documentado separadamente em `n8n.md` neste mesmo diretório).
- Não substitui o smoke test manual pós-deploy contra a Darwin real (ver
  relatório de endurecimento — a escrita real na Darwin não foi validada por
  esta tarefa).
