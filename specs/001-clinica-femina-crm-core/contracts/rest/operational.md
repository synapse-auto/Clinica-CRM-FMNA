# Contratos REST — Operacionais (Lembretes, Tags, Mensagens Rápidas, Automação, Horários, Equipe, Dashboard, Satisfação, IA, Configurações, LGPD)

## Lembretes (Diagrama: Lembrete)

### GET /api/lembretes
**Query**: `pacienteId?`, `pendentes=true`, `devidosAntesDe=<iso>`.

### POST /api/lembretes
```json
{ "pacienteId": 9876, "dataHoraProgramada": "2026-05-20T09:00:00Z", "mensagem": "Confirmar exame de sangue" }
```
Response 201: objeto lembrete.

### PATCH /api/lembretes/{id}
Atualiza `dataHoraProgramada`, `mensagem`. Não editável após disparado.

### DELETE /api/lembretes/{id}
Cancela (set `cancelado=true`). 204.

---

## Tags (Diagrama: Tag)

### GET /api/tags
```json
{
  "itens": [
    { "id": 1, "nome": "Consulta Pré-natal", "cor": "#3B82F6", "contagemLeads": 10, "percentual": 0.37 }
  ]
}
```

### POST /api/tags
```json
{ "nome": "VIP", "cor": "#F59E0B" }
```

### PATCH /api/tags/{id}
Atualiza `nome` ou `cor`.

### DELETE /api/tags/{id}
Soft delete; remove associação de todos os pacientes (cascata).

---

## Mensagens Rápidas (Diagrama: MensagemRapida — `atalho`, `texto`, `categoria`)

Todos endpoints escopados ao usuário corrente (BR-04).

### GET /api/mensagens-rapidas
**Query**: `categoria?`, `q?`.

### POST /api/mensagens-rapidas
```json
{ "titulo": "Boas-vindas", "atalho": "/abertura", "categoria": "ABERTURA", "texto": "Olá! Bem-vindo(a) à Clínica Femina 😊" }
```

### PATCH /api/mensagens-rapidas/{id}

### DELETE /api/mensagens-rapidas/{id}
Soft delete.

### GET /api/mensagens-rapidas/categorias
```json
{ "itens": [ { "codigo": "ABERTURA", "rotulo": "Abertura", "cor": "#3B82F6" }, ... ] }
```

---

## Regras de Automação (Diagrama: RegraAutomacao)

### GET /api/automacao/regras
```json
{
  "itens": [
    {
      "tipo": "REMINDER_48H",
      "rotulo": "Lembrete 48h antes",
      "ativo": true,
      "offsetMinutos": -2880,
      "thresholdDias": null,
      "templateMensagem": "Olá {paciente}! Lembramos que sua consulta com {medico} é em 48h..."
    }
  ]
}
```

### PATCH /api/automacao/regras/{tipo}
```json
{ "ativo": true, "templateMensagem": "Olá {paciente}! ..." }
```

---

## Janela Horário IA & Horário Atendente (Diagrama: JanelaHorarioIA)

### GET /api/horarios/janela-ia
```json
{
  "ia24h": false,
  "janelas": [
    { "diaSemana": "SEGUNDA", "horaInicio": "05:00", "horaFim": "23:59" },
    { "diaSemana": "TERCA",   "horaInicio": "05:00", "horaFim": "23:59" }
  ]
}
```

### PUT /api/horarios/janela-ia
Substitui janelas (1 linha por dia). `ia24h: true` bypassa todas as janelas.

### GET /api/horarios/atendentes
```json
{
  "itens": [
    {
      "usuario": { "id": 7, "nome": "Ana Lima", "perfil": "RECEPCIONISTA" },
      "online": true,
      "semanal": [
        { "diaSemana": "SEGUNDA", "horaInicio": "08:00", "horaFim": "18:00", "ativo": true }
      ]
    }
  ]
}
```

### PUT /api/horarios/atendentes/{usuarioId}
Substitui horário de trabalho do atendente.

### POST /api/horarios/aplicar-padrao
Aplica as janelas da IA como horário padrão para todos os atendentes.

---

## Equipe

### GET /api/equipe
```json
{
  "gestor": { ... },
  "medicos": [
    { "id": 3, "nome": "Dra. Renata Fiuza", "especialidade": "OBSTETRICIA_PRE_NATAL", "online": true }
  ],
  "recepcionistas": [
    { "id": 7, "nome": "Ana Lima", "online": true, "atendimentosAtivos": 14, "tempoMedioRespostaSegundos": 192 }
  ]
}
```

Read-only em v1 (FR-TEAM-04).

---

## Dashboard

### GET /api/dashboard
**Query**: `escopo` (`DIA` | `SEMANA` | `MES`), `data` (data âncora).

**Response 200**
```json
{
  "escopo": "DIA",
  "data": "2026-05-19",
  "cards": [
    { "codigo": "EQUIPE_ONLINE",        "valor": 3, "delta": 1,    "deltaUnidade": "vs ontem" },
    { "codigo": "NOVOS_PACIENTES",      "valor": 12, "delta": 0.33, "deltaUnidade": "vs ontem" },
    { "codigo": "TOTAL_MENSAGENS",      "valor": 547, "delta": 0.18 },
    { "codigo": "AGENDADOS",            "valor": 47 },
    { "codigo": "CONFIRMACOES_PENDENTES","valor": 4, "delta": -2 },
    { "codigo": "TEMPO_MEDIO_RESPOSTA_MIN", "valor": 4.2, "delta": -0.8 }
  ],
  "mensagensPorHora":      [ { "hora": 0, "contagem": 1 }, ... ],
  "pacientesDaSemana":     { "novos": 12, "recorrentes": 8, "agendados": 24, "followUp": 7 },
  "agendamentosSemanais":  [ { "dia": "SEG", "consulta": 7, "exame": 3, "cirurgia": 0, "retorno": 2 } ],
  "distribuicaoServicos":  { "PRE_NATAL": 0.35, "GINECOLOGIA": 0.28, "ULTRASSONOGRAFIA": 0.18, "CIRURGIAS": 0.19 },
  "taxaFidelizacao": 0.62
}
```

---

## Satisfação (Diagrama: PesquisaSatisfacao)

### GET /api/satisfacao/respostas
**Query**: `de`, `ate`, `medicoId?`, `notaMin?`, `notaMax?`.

### GET /api/satisfacao/resumo
```json
{ "media": 9.2, "distribuicao": { "0": 0, "1": 0, ..., "10": 42 }, "evolucao": [ { "mes": "2026-04", "media": 9.0 } ] }
```

---

## Cancelamentos por IA

### GET /api/cancelamentos-ia
**Query**: `de`, `ate`, `medicoId?`, `codigoMotivo?`.

```json
{
  "itens": [
    {
      "agendamentoId": 5001,
      "paciente": { "id": 9876, "nome": "Maria Fernanda Santos" },
      "medico":  { "id": 3, "nome": "Dra. Renata Fiuza" },
      "dataHoraOriginal": "2026-05-20T10:00:00Z",
      "codigoMotivo": "PEDIDO_PACIENTE",
      "motivo": "Paciente reagendou por conflito pessoal",
      "canceladoEm": "2026-05-19T08:00:00Z",
      "atendimentoId": 12345
    }
  ],
  "totalCancelamentos": 17,
  "topMotivos": [ { "codigoMotivo": "PEDIDO_PACIENTE", "contagem": 9 } ],
  "porMedico":   [ { "medicoId": 3, "contagem": 5 } ]
}
```

---

## Configurações

### GET /api/configuracoes
### PUT /api/configuracoes/notificacoes
### PUT /api/configuracoes/acesso     *(Gestor apenas)*
### PUT /api/configuracoes/clinica    *(Gestor apenas)*
### PUT /api/configuracoes/tema       *(por usuário)*

---

## LGPD

### GET /api/lgpd/log-auditoria
**Query**: `entidadeAlvo?`, `idAlvo?`, `de?`, `ate?`, `atorId?`, `acao?`.

### POST /api/lgpd/exportar/paciente/{id}
**Request**
```json
{ "formato": "CSV" | "XLSX", "motivo": "Solicitação do titular conforme Art.18 IV LGPD" }
```
**Response 202**: `{ "jobExportacaoId": "exp_abc123" }`. Job async; arquivo via:

### GET /api/lgpd/exportar/{jobId}
Retorna o arquivo (CSV ou XLSX) quando pronto, ou `202 Accepted` se ainda processando.

### POST /api/lgpd/eliminar/paciente/{id}
Hard delete (Gestor apenas + motivo obrigatório).

### POST /api/lgpd/incidente
Abre relatório de incidente.

### GET /api/lgpd/consentimento/{pacienteId}
Lista consentimentos do paciente.

### POST /api/lgpd/consentimento/{pacienteId}
Captura novo consentimento.
