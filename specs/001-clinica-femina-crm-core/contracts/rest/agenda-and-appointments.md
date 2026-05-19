# Contratos REST — Agenda & Agendamentos

Mapeia a entidade `Agendamento` do Diagrama (com `dataHora`, `tipoServico`, `status`, `motivoCancelamentoIA`).

## GET /api/agenda/semana

Visão semanal. Default = semana ISO atual.

**Query params**
| Param | Notas |
|-------|-------|
| inicioSemana | data ISO (segunda-feira) — default semana atual |
| medicoId | filtra para um médico |

**Response 200**
```json
{
  "inicioSemana": "2026-05-18",
  "resumo": {
    "consultasHoje": 7,
    "totalSemanal": 81,
    "medicosDisponiveis": 4,
    "taxaOcupacao": 0.78
  },
  "porTipo": {
    "CONSULTA": 49, "EXAME": 25, "CIRURGIA": 7, "RETORNO": 14
  },
  "porMedico": [
    {
      "medico": { "id": 3, "nome": "Dra. Renata Fiuza", "especialidade": "OBSTETRICIA_PRE_NATAL" },
      "contagens": { "CONSULTA": 12, "EXAME": 3, "CIRURGIA": 0, "RETORNO": 5 }
    }
  ],
  "dias": [
    {
      "data": "2026-05-18",
      "diaSemana": "SEGUNDA",
      "agendamentos": [
        { "id": 5001, "paciente": { "id": 100, "nome": "Beatriz" }, "hora": "10:00", "tipoServico": "CONSULTA", "medicoId": 3, "status": "AGENDADO" }
      ]
    }
  ]
}
```

---

## GET /api/agendamentos

Lista filtrada com data range + status.

**Query**: `de`, `ate` (data), `status`, `medicoId`, `pacienteId`, `origem`.

---

## POST /api/agendamentos

Cria agendamento (human-scheduled).

**Request**
```json
{
  "pacienteId": 9876,
  "medicoId": 3,
  "tipoServico": "CONSULTA",
  "dataHora": "2026-05-20T10:00:00Z",
  "dataHoraFim": "2026-05-20T10:30:00Z",
  "valor": 350.00,
  "notasClinicas": "Primeira consulta de pré-natal"
}
```

**Response 201**: agendamento completo com `origem = "HUMANO"`.

---

## PATCH /api/agendamentos/{id}

Transições permitidas:
- `AGENDADO → CONFIRMADO`
- `AGENDADO → CANCELADO`
- `CONFIRMADO → CONCLUIDO`
- `CONFIRMADO → NO_SHOW`
- `CONFIRMADO → CANCELADO`

**Request**
```json
{ "status": "CONFIRMADO" }
```

Transições inválidas retornam `409 REGRA_NEGOCIO_VIOLADA`.

---

## DELETE /api/agendamentos/{id}

Cancela (set `status=CANCELADO`, `cancelado_em=NOW()`). Sem delete físico.

---

## GET /api/agenda/ocupacao

Calcula taxa de ocupação por médico no período — usado no Dashboard.
