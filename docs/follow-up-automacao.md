# Follow-up, lembretes e mensagens festivas por clinica

## Escopo desta etapa

Esta etapa adiciona o minimo seguro para a UltraMedical iniciar homologacao com follow-up operacional:

- configuracao de follow-up por clinica em `follow_up_config`;
- configuracao de lembrete de consulta em `consulta_lembrete_config`;
- configuracao de mensagens festivas/feriados em `mensagem_festiva_config`;
- fila operacional de follow-ups em `follow_ups_temporary`, sempre vinculada a `paciente_id` e `scheduled_at`;
- eventos N8N emitidos pelo backend, sem acesso direto do N8N ao banco.

A demo analisada e uma referencia visual com dados mockados. Tags, mensagens rapidas, funil e origem de lead normalizada ficam para migration propria depois de confirmacao do cliente.

## Modelo

`follow_up_config` define regras reutilizaveis, como gatilho, canal, delay e template.

`consulta_lembrete_config` define como o CRM deve alimentar o N8N para lembretes de consulta. Ela guarda antecedencia, canal, horario sugerido e flags para confirmacao, cancelamento e reagendamento.

`mensagem_festiva_config` define mensagens por feriado ou data comemorativa. Cada registro tem `ativo`, `chave`, `mes_dia` e `mensagem_template`, permitindo ligar/desligar cada campanha individualmente.

`follow_ups_temporary` e a fila operacional temporaria. Ela guarda:

- `clinica_id`: clinica dona do registro;
- `paciente_id`: FK obrigatoria para `paciente(id)`;
- `follow_up_config_id`: vinculo opcional com a configuracao que originou o item;
- `scheduled_at`: data e horario do follow-up em `TIMESTAMPTZ`;
- `status`: `PENDENTE`, `PROCESSANDO`, `PROCESSADO`, `EXECUTADO`, `CANCELADO` ou `FALHOU`;
- `payload`: JSONB para metadados de automacao sem criar schema prematuro.

O horario usa `TIMESTAMPTZ` para manter armazenamento consistente em UTC e permitir exibicao em `America/Sao_Paulo` na camada de UI/API.

## API

Configuracao de follow-up:

```http
GET /api/follow-up/config
POST /api/follow-up/config
PUT /api/follow-up/config/{id}
PATCH /api/follow-up/config/{id}/status
```

Configuracao de lembrete de consulta:

```http
GET /api/consulta-lembrete/config
POST /api/consulta-lembrete/config
PUT /api/consulta-lembrete/config/{id}
PATCH /api/consulta-lembrete/config/{id}/status
```

Configuracao de mensagens festivas:

```http
GET /api/mensagens-festivas/config
POST /api/mensagens-festivas/config
PUT /api/mensagens-festivas/config/{id}
PATCH /api/mensagens-festivas/config/{id}/status
```

Fila temporaria de follow-ups:

```http
GET /api/follow-ups-temporary?status=&pacienteId=&from=&to=
GET /api/pacientes/{pacienteId}/follow-ups-temporary
POST /api/pacientes/{pacienteId}/follow-ups-temporary
PATCH /api/follow-ups-temporary/{id}/status
```

Nenhum endpoint recebe `clinicaId` no body ou query. A clinica vem do `ClinicaConfigService`, que usa a configuracao do deploy atual.

## Eventos N8N

Eventos emitidos:

- `follow_up_criado`;
- `follow_up_executado`;
- `follow_up_cancelado`;

N8N deve chamar a API do backend para consultar, criar ou alterar follow-ups temporarios. Nao fazer insert direto em `follow_ups_temporary`, porque a regra de clinica, validacao de paciente e emissao de eventos fica no service.

## Pendencias frontend

- tela para CRUD de `follow_up_config`;
- tela para CRUD de `consulta_lembrete_config`;
- tela para CRUD de `mensagem_festiva_config`;
- lista de follow-ups por paciente;
- filtro operacional por status e periodo;
- acoes de executar/cancelar follow-up.
