# Contratos REST — Pacientes

Mapeia diretamente a entidade `Paciente` do Diagrama (com `nome`, `telefone`, `email`, `horarioPreferencial`, `notasInternas`, `status`, `deletedAt`, `alterarStatus()`).

## GET /api/pacientes

Lista pacientes com filtros. Suporta lista e Kanban via `visao`.

**Query params**
| Param | Valores | Notas |
|-------|---------|-------|
| q | string | busca por nome (trigram) ou telefone (E.164) — NFR-07 ≤ 1s |
| status | `EM_ATENDIMENTO` \| `AGENDADO` \| `FOLLOW_UP` \| `FINALIZADO` | repetível |
| tagId | int64 | repetível |
| atendenteId | int64 | filtra por atendente responsável |
| incluirFollowUp | boolean | default true |
| visao | `LISTA` \| `KANBAN` | default `LISTA` |
| pagina, tamanho | int | paginação |

**Response 200 (visão LISTA)**
```json
{
  "itens": [
    {
      "id": 9876,
      "nome": "Maria Fernanda Santos",
      "contato": { "telefone": "+554498765432", "email": "mariaferns@gmail.com" },
      "status": "EM_ATENDIMENTO",
      "followUpAtivo": false,
      "tags": [{ "id": 5, "nome": "Consulta Pré-natal", "cor": "#3B82F6" }],
      "valorTotal": 850.00,
      "atendentePrincipal": { "id": 7, "nome": "Ana Lima" }
    }
  ],
  "totalElementos": 27, "somaValorTotal": 119450.00
}
```

**Response 200 (visão KANBAN)**
```json
{
  "colunas": [
    { "status": "EM_ATENDIMENTO", "itens": [ ... ], "totalElementos": 8 },
    { "status": "AGENDADO",       "itens": [ ... ], "totalElementos": 12 },
    { "status": "FOLLOW_UP",      "itens": [ ... ], "totalElementos": 4 },
    { "status": "FINALIZADO",     "itens": [ ... ], "totalElementos": 3 }
  ]
}
```

---

## POST /api/pacientes

Cria novo paciente.

**Request**
```json
{
  "nome": "Joana Pereira",
  "cpf": "111.222.333-44",
  "dataNascimento": "1992-08-15",
  "email": "joana@example.com",
  "telefone": "+554499998888",
  "horarioPreferencial": "TARDE",
  "notasInternas": "Encaminhada pela Dra. Helena",
  "medicoPrincipalId": 3,
  "tagIds": [5, 8]
}
```

**Response 201**: objeto Paciente completo.

**Validação**: `nome` e `telefone` obrigatórios; CPF normalizado para apenas dígitos + checksum.

---

## GET /api/pacientes/{id}

Detalhe completo (descriptografado em camada de controller se autorizado).

---

## PATCH /api/pacientes/{id}

Update parcial. Campos permitidos: `nome`, `email`, `horarioPreferencial`, `notasInternas`, `status`, `followUpAtivo`, `atendentePrincipalId`, `medicoPrincipalId`, `tagIds`, `valorTotal`.

**Enforcement de regras**:
- Mudança de `status` valida grafo de transição BR-01 (implementa `Paciente.alterarStatus()` do Diagrama).
- Toggle de `followUpAtivo` é independente.

---

## DELETE /api/pacientes/{id}

Soft delete (BR-02). Retorna 204.

**Audit**: grava linha em `log_auditoria` com `acao=SOFT_DELETE_PACIENTE`.

---

## POST /api/pacientes/{id}/tags/{tagId}

Aplica tag. Retorna 204.

## DELETE /api/pacientes/{id}/tags/{tagId}

Remove tag. Retorna 204.

---

## GET /api/pacientes/{id}/timeline

Timeline composta: mensagens, agendamentos, lembretes, automações, mudanças de status — para a view de perfil do paciente.

**Response**: array de eventos timeline ordenado desc por `ocorridoEm`.

---

## GET /api/pacientes/{id}/historico-consultas

```json
{
  "itens": [
    { "id": 901, "tipoServico": "CONSULTA", "nomeMedico": "Dra. Renata Fiuza", "concluidoEm": "2026-04-12T14:00:00Z", "valor": 350.00 }
  ]
}
```
