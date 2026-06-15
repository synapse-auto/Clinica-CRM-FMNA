# Mapa da API Medware para o CRM

Referencia consultada: documentacao oficial Medware em `https://apiclinicas.medware.com.br/api/swagger/index.html` e contrato OpenAPI `v1.5.3`.

## Escopo desta fase

A integracao Medware do CRM e somente leitura. O CRM autentica na API Medware, consulta pacientes, agendamentos e catalogos auxiliares, normaliza os dados para `ExternalPatientDTO` e `ExternalAppointmentDTO`, e deixa o `ExternalSyncService` gravar o modelo interno (`paciente`, `agendamento`, `integration_sync_log`).

Nao ha escrita no Medware nesta fase.

## URL e autenticacao

- A URL real deve ser configurada por deploy em `MEDWARE_API_URL`.
- A documentacao indica que cada instalacao Medware roda no ambiente do cliente e deve usar o DNS/publicacao da propria clinica.
- Quando a publicacao seguir o padrao da documentacao, usar o sufixo `/api`.
- Login: `POST /Acesso/login`.
- Payload de login:
  - `identificacao`
  - `senha`
  - `isHash`
- O token JWT retorna em `token` e deve ser enviado como `Authorization: Bearer <token>`.
- A documentacao informa token padrao de 24 horas e renovacao por `POST /Acesso/refreshToken?refreshToken=...&token=...`.
- O provider faz cache em memoria e tenta renovar quando existir `refreshToken`; se nao houver refresh, faz novo login.
- Datas em query de filtros usam `dd/MM/yyyy`.

## Variaveis de ambiente

```text
MEDWARE_API_URL=<url-publica-da-instalacao-medware-ultramedical>/api
MEDWARE_USERNAME=<usuario-api-medware>
MEDWARE_PASSWORD=<senha-ou-hash>
MEDWARE_PASSWORD_IS_HASH=true
MEDWARE_TOKEN_REFRESH_MARGIN_SECONDS=300
MEDWARE_TIMEOUT_SECONDS=30
MEDWARE_DEFAULT_START_DAYS_BACK=30
MEDWARE_DEFAULT_END_DAYS_FORWARD=60
MEDWARE_WEBHOOK_TOKEN=<token-webhook-apenas-etapa-futura>
```

`MEDWARE_WEBHOOK_TOKEN` esta documentado para etapa futura, mas o CRM nao usa webhook Medware nesta fase read-only.

## Endpoints read-only avaliados

| Endpoint | Uso no CRM | Observacao |
| --- | --- | --- |
| `POST /Acesso/login` | Implementado | Autenticacao JWT. |
| `POST /Acesso/refreshToken` | Implementado como tentativa | Usado apenas se a resposta de login trouxer `refreshToken`. |
| `GET /Medware/Paciente/Listar` | Implementado | Sem paginacao documentada; provider pagina localmente a lista retornada. |
| `GET /Medware/Agendamento/Listar` | Implementado | Usa janela `dataInicio`/`dataFim`; `ultimaDataHora` existe no OpenAPI, mas o formato nao ficou claro. |
| `GET /Medware/Medico/Listar` | Implementado como catalogo auxiliar | Enriquece agendamento com nome do medico quando disponivel. |
| `GET /Medware/ProcedPlanoOp/Listar` | Implementado como catalogo auxiliar | Enriquece procedimento/servico e duracao quando disponivel. |
| `GET /Medware/Especialidade/Listar` | Documentado | Ainda nao necessario para o DTO interno atual. |
| `GET /Medware/PlanoOperadora/Listar` | Documentado | Ainda nao necessario para o DTO interno atual. |
| `GET /Medware/Plano/Listar` | Documentado | Ainda nao necessario para o DTO interno atual. |
| `GET /Medware/Unidade/Listar` | Documentado | Ainda nao necessario para o DTO interno atual. |
| `GET /Medware/Horarios/Listar` | Documentado | Lista slots disponiveis; nao representa agenda ja marcada para sync interno. |
| `GET /Medware/Agendamento/ListarStatus` | Documentado | Pode ser usado futuramente para mapear codigos de status com mais precisao. |
| `GET /Medware/Avaliacao/Listar` | Documentado | Fonte candidata para satisfacao/NPS. Ainda nao integrada ao Dashboard. |
| `GET /Licenca/Modulo` | Documentado | Pode ser usado em health check futuro da integracao. |

## Endpoints de escrita ignorados nesta fase

| Endpoint | Motivo |
| --- | --- |
| `POST /Medware/Agendamento/Salvar` | Escreve agendamento no Medware. |
| `PUT /Medware/Paciente/Atualizar` | Atualiza cadastro no Medware. |
| `PUT /Medware/Agendamento/Status` | Atualiza status no Medware. |
| `PUT /Medware/Agendamento/CancelarAgendamento` | Cancela agendamento no Medware. |
| `POST /Medware/Agendamento/Encaixe` | Escreve encaixe no Medware. |
| `POST /Medware/AgendamentoWeb/Salvar` | Escreve agendamento web. |
| `POST /Medware/Avaliacao/Salvar` | Escreve avaliacao. |
| `POST /Medware/Resultado/Salvar` | Escreve resultado. |
| `POST /MedwareWebHook/MedwareWebHook` | Grava confirmacoes/avaliacoes no Medware; apenas documentado para etapa futura. |

## Mapeamento de paciente

| CRM `ExternalPatientDTO` | Campo Medware |
| --- | --- |
| `externalId` | `codPaciente` |
| `fullName` | `nome`, `nomePaciente` |
| `documentNumber` | `cpf`, `cpfPaciente`, `documento` |
| `email` | `email`, `emailPaciente` |
| `phone` | `numeroCelularddd` + `numeroCelular`; fallback para `telefonePaciente`, `telefone`, `celular` ou array `telefones` |
| `birthDate` | `dataNascimento`, `dataNasc`, `nascimento` |
| `updatedAt` | `updatedAt`, `atualizadoEm`, `ultimaAtualizacao`, `ultimaDataHora`, se existir |
| `payload` | JSON bruto do registro Medware |

## Mapeamento de agendamento

| CRM `ExternalAppointmentDTO` | Campo Medware |
| --- | --- |
| `externalId` | `codAgendamento`, `idAgendamento`, `id`; fallback `codAgenda` |
| `externalPatientId` | `codPaciente`, `idPaciente` |
| `startAt` | `dataHoraAgendada`, `dataHoraInicio`, `dataHora`, `inicio`; fallback data + hora |
| `endAt` | `dataHoraFim`, `fim`, `dataHoraFinal`; fallback `startAt + duracao` quando houver duracao |
| `type` | `tipo`, `tipoAtendimento`, `tipoProcedimento`; fallback pelo campo `consulta` do procedimento |
| `serviceName` | `servicoNome`, `procedimento`, `descricaoProcedimento`, `nomeProcedimento`; fallback catalogo `ProcedPlanoOp` |
| `status` | Texto de status quando vier; fallback conservador para codigos conhecidos e `AGENDADO` |
| `confirmedAt` | `confirmadoEm`, `dataConfirmacao`, `dataHoraConfirmacao`; fallback start quando status confirmado |
| `canceledAt` | `canceladoEm`, `dataCancelamento`, `dataHoraCancelamento`; fallback start quando status cancelado |
| `cancellationReason` | `motivoCancelamento`, `motivo`, `observacaoCancelamento` |
| `payload` | JSON bruto do agendamento + catalogos auxiliares disponiveis |

## Pendencias de campo/fornecedor

- Confirmar se `GET /Medware/Paciente/Listar` sem filtro e permitido em bases grandes.
- Confirmar se existe paginacao ou limite servidor nos endpoints `Listar`.
- Confirmar formato e semantica de `ultimaDataHora` em `Agendamento/Listar`.
- Confirmar lista oficial de `codStatusAgendamento`.
- Confirmar se `Agendamento/Listar` retorna nome de medico, procedimento, plano, unidade e especialidade em algum ambiente real.
- Confirmar se existe endpoint read-only para notas clinicas/prontuario; nao foi identificado endpoint claro equivalente a notas clinicas.
- Confirmar limites de taxa; a documentacao consultada nao trouxe rate limit explicito.

## Dashboard UltraMedical por fonte

| Metrica | Fonte principal |
| --- | --- |
| equipe online | Backend interno/WebSocket |
| novos pacientes | Modelo interno `paciente`, alimentado por WhatsApp e Medware |
| total de mensagens | WhatsApp/modelo interno `mensagem` |
| consultas/exames agendados | Modelo interno `agendamento`, alimentado por Medware |
| confirmacoes pendentes | Modelo interno + WhatsApp/N8N; Medware pode fornecer agenda/status quando mapeamento for confirmado |
| tempo medio de resposta | Backend interno, calculado sobre mensagens |
| pico de mensagens por hora | Backend interno, calculado sobre mensagens |
| distribuicao de servicos | Modelo interno `agendamento.servico_nome`, alimentado por Medware |
| taxa de fidelizacao | Calculo interno sobre recorrencia de pacientes/agendamentos |
| cancelamentos por IA | N8N/IA + eventos internos; nao vem direto do provider Medware nesta fase |
| satisfacao/NPS | Pendente; candidato `GET /Medware/Avaliacao/Listar` ou fluxo N8N/IA |
| follow-up | N8N/IA + regras internas |

## Erros esperados

- `400`: erro de validacao/filtros invalidos.
- `401`: token ausente, invalido ou expirado; provider invalida cache e a proxima chamada tenta novo login.
- `404`: endpoint/recurso nao encontrado.
- `503`: indisponibilidade temporaria da instalacao/API.

Os logs do provider registram endpoint e tipo/status do erro sem token, senha, telefone, nome de paciente ou conteudo clinico.
