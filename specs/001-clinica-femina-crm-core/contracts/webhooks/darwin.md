# Contrato de Integração — API Darwin (Consumo Read-only)

Mapeia a interface `APIDarwin` do Diagrama de Classes (`importarPacientes()`, `importarAgendamentos()`, `importarDadosClinicos()`).

**Direção**: Sistema ← Darwin **apenas** (FR-INT-04 / RF17).

**Agendamento**: Quartz cron `DarwinSyncJob` roda a cada 15 minutos (configurável via env `DARWIN_SYNC_CRON`). Triggers on-demand disponíveis ao Gestor via `POST /api/integracoes/darwin/sincronizar`.

**Auth**: Bearer token (`DARWIN_API_TOKEN`) emitido pelo admin Darwin; rotação trimestral.

**Base URL**: `https://api.darwin.health/v1` (configurável por ambiente).

---

## Recursos Sincronizados

### Pacientes (`importarPacientes()` no Diagrama)

`GET /v1/patients?updated_after=<iso>&page=<n>&size=200`

**Response (trecho — payload nativo do Darwin em inglês, traduzido na camada de mapping)**
```json
{
  "items": [
    {
      "darwinId": "darwin_p_001",
      "fullName": "Maria Fernanda Santos",
      "cpf": "111.222.333-44",
      "birthDate": "1989-04-12",
      "email": "mariaferns@gmail.com",
      "phone": "+554498765432",
      "address": { "street": "...", "city": "Maringá" },
      "primaryDoctorDarwinId": "darwin_d_003",
      "updatedAt": "2026-05-18T20:00:00Z"
    }
  ],
  "nextPage": null
}
```

**Mapping** (Darwin → tabela `paciente`):

| Campo Darwin | Coluna `paciente` |
|--------------|-------------------|
| `darwinId` | `darwin_id_externo` |
| `fullName` | `nome` (🔒 encrypt) + `nome_busca` (lowercase) |
| `cpf` | `cpf` (🔒 encrypt) + `cpf_hash` |
| `birthDate` | `data_nascimento` (🔒) |
| `email` | `email` (🔒) + `email_hash` |
| `phone` | `telefone` (🔒) + `telefone_normalizado` |
| `address` | `endereco` (🔒 JSONB) |
| `primaryDoctorDarwinId` | resolve para `medico_principal_id` (FK `usuario(id)`) |
| `updatedAt` | `atualizado_em` |

- Match por `darwin_id_externo` (1:1 com `paciente.darwin_id_externo`).
- Se não encontrar, tenta match por `cpf_hash` (SHA-256 peppered) — se encontrar, vincula `darwin_id_externo`.
- Caso contrário, insere novo paciente com `darwin_id_externo`.
- Updates aplicam apenas em colunas NÃO modificadas localmente desde `estado_sync_darwin.ultimo_sync_em`. Conflitos setam `paciente.requer_revisao = true`.

### Agendamentos (`importarAgendamentos()` no Diagrama)

`GET /v1/appointments?updated_after=<iso>&page=<n>&size=200`

**Mapping**:
- Match por `darwin_id_externo`.
- Sempre `origem = 'DARWIN'` nestes registros.
- Updates permitidos (Darwin é sistema-of-record para eventos clínicos).

### Notas Clínicas (`importarDadosClinicos()` no Diagrama)

`GET /v1/patients/{darwinId}/notes?updated_after=<iso>`

Armazenado como JSONB em `paciente.darwin_dados_importados.notasClinicas`. Exibido em painel read-only de detalhe do paciente para Médicos.

---

## Tratamento de Falhas

- HTTP 5xx ou timeout → marca `estado_sync_darwin.ultimo_status = 'FALHOU'`, persiste `ultimo_erro`, alerta Gestor.
- HTTP 401 → alerta automático ao DPO + Gestor (provável rotação de token).
- Schema drift (campo desconhecido, campo obrigatório ausente) → log warning, skip record, continua batch.

---

## UI do Gestor

Seção pequena "Integrações" dentro de Configurações mostra:
- Último sync timestamp por recurso.
- Último status e erro se houver.
- Botão "Sincronizar agora" (apenas Gestor, rate-limit 1/min).

---

## Garantia No-Outbound

A classe `DarwinClient` expõe apenas métodos `GET`. Qualquer tentativa de `POST`/`PUT`/`PATCH`/`DELETE` é ausência em tempo de compilação (método não definido) — guardado por item de checklist de code review vinculado a FR-INT-04.
