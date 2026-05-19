# Contratos REST — Atendimentos & Mensagens

Mapeia diretamente a entidade `Atendimento` do Diagrama de Classes (com `dataInicio`, `status`, `transferirAtendente()`) e a entidade `Mensagem`.

## GET /api/atendimentos

Lista atendimentos visíveis ao usuário corrente. Recepcionista vê próprios + não-atribuídos; Gestor vê todos; Médico vê próprios.

**Query params**
| Param | Tipo | Notas |
|-------|------|-------|
| filtro | `TODOS` \| `IA` \| `HUMANO` | default `TODOS` |
| q | string | busca por nome ou telefone do paciente |
| status | `ATIVO` \| `TRANSFERIDO` \| `ENCERRADO` \| `IA_AUTOMATICO` | default `ATIVO,IA_AUTOMATICO` |
| pagina | int | default 0 |
| tamanho | int | default 30, max 100 |

**Response 200**
```json
{
  "itens": [
    {
      "id": 12345,
      "dataInicio": "2026-05-19T14:00:00Z",
      "status": "ATIVO",
      "tratadoPorIa": false,
      "paciente": {
        "id": 9876,
        "nome": "Maria Fernanda Santos",
        "telefone": "+554498765432",
        "status": "EM_ATENDIMENTO",
        "tags": [{ "id": 5, "nome": "Consulta Pré-natal", "cor": "#3B82F6" }]
      },
      "atendentePrincipal": { "id": 7, "nome": "Ana Lima" },
      "ultimaMensagem": {
        "previa": "Eu gostaria de agendar uma consulta...",
        "direcao": "ENTRADA",
        "dataHora": "2026-05-19T14:31:00Z"
      },
      "naoLidas": 2
    }
  ],
  "pagina": 0, "tamanho": 30, "totalElementos": 47, "totalPaginas": 2
}
```

---

## GET /api/atendimentos/{id}

Detalhe de um atendimento (contexto do paciente + contadores).

**Response 200**
```json
{
  "id": 12345,
  "dataInicio": "2026-05-19T14:00:00Z",
  "dataEncerramento": null,
  "status": "ATIVO",
  "tratadoPorIa": false,
  "paciente": {
    "id": 9876,
    "nome": "Maria Fernanda Santos",
    "email": "mariaferns@gmail.com",
    "telefone": "+554498765432",
    "horarioPreferencial": "MANHA",
    "notasInternas": "Paciente ansiosa, prefere comunicação calma",
    "medicoPrincipal": { "id": 3, "nome": "Dra. Renata Fiuza", "especialidade": "OBSTETRICIA_PRE_NATAL" },
    "tags": [{ "id": 5, "nome": "Consulta Pré-natal", "cor": "#3B82F6" }],
    "valorTotal": 850.00,
    "ultimoProcedimento": { "tipoServico": "CONSULTA", "concluidoEm": "2026-04-12T14:00:00Z" },
    "historicoConsultas": [
      { "id": 901, "tipoServico": "CONSULTA", "medico": "Dra. Renata Fiuza", "concluidoEm": "2026-04-12T14:00:00Z" }
    ],
    "lembretesAtivos": [
      { "id": 401, "dataHoraProgramada": "2026-05-20T09:00:00Z", "mensagem": "Lembrar de confirmar exame" }
    ]
  },
  "atendentePrincipal": { "id": 7, "nome": "Ana Lima" },
  "naoLidas": 2
}
```

---

## GET /api/atendimentos/{id}/mensagens

Histórico paginado de mensagens (desc por data_hora).

**Query params**: `antes` (cursor), `tamanho` (default 50, max 200).

**Response 200**
```json
{
  "itens": [
    {
      "id": 88123,
      "direcao": "ENTRADA",
      "remetente": "PACIENTE",
      "tipoMedia": "TEXTO",
      "conteudo": "Eu gostaria de agendar uma consulta de pré-natal",
      "dataHora": "2026-05-19T14:31:00Z"
    },
    {
      "id": 88124,
      "direcao": "SAIDA",
      "remetente": "ATENDENTE",
      "remetenteUsuario": { "id": 7, "nome": "Ana Lima" },
      "tipoMedia": "TEXTO",
      "conteudo": "Olá Maria Fernanda! 😊 Vou te ajudar com o agendamento...",
      "dataHora": "2026-05-19T14:32:11Z",
      "whatsappStatus": "LIDA",
      "lidaEm": "2026-05-19T14:32:30Z"
    }
  ],
  "proximoCursor": "88100"
}
```

---

## POST /api/atendimentos/{id}/mensagens

Envia mensagem outbound via WhatsApp.

**Request** (texto)
```json
{ "tipoMedia": "TEXTO", "conteudo": "Olá, sua consulta foi confirmada." }
```

**Request** (mídia)
```json
{ "tipoMedia": "IMAGEM", "conteudo": "Veja o preparo:", "midiaUploadId": "upl_abc123" }
```

**Response 202**
```json
{ "id": 88125, "whatsappStatus": "ENVIADA", "dataHora": "2026-05-19T14:35:00Z" }
```

---

## POST /api/atendimentos/{id}/transferir

Implementa o método `Atendimento.transferirAtendente()` do Diagrama (BR-05).

**Request**
```json
{ "paraUsuarioId": 12, "motivo": "Maria pediu para falar com um médico" }
```

**Response 200**: atendimento atualizado com novo `atendentePrincipal` e status `TRANSFERIDO` momentâneo (volta para `ATIVO` no novo atendente).

**Response 403**: usuário sem capacidade `TRANSFER_CONVERSATIONS`.

---

## POST /api/atendimentos/{id}/encerrar

Encerra o atendimento (status → `ENCERRADO`).

**Request**
```json
{ "motivo": "Atendimento concluído" }
```

**Response 200**: atendimento com `dataEncerramento` preenchido.

Se houver agendamento `CONCLUIDO` associado e regra `POST_CONSULT_SURVEY` ativa, dispara pesquisa de satisfação após delay configurável.

---

## POST /api/atendimentos/{id}/uploads-midia

Pre-flight de upload de mídia outbound (max 16MB imagem, 16MB áudio, 100MB documento).

**Request**: multipart/form-data com campo `arquivo`.

**Response 201**
```json
{ "uploadId": "upl_abc123", "expiraEm": "2026-05-19T15:00:00Z" }
```
