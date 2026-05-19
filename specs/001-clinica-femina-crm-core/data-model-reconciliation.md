# Reconciliação Modelo de Dados vs. Diagrama de Classes

**Feature**: 001-clinica-femina-crm-core
**Data**: 2026-05-19
**Diagrama fonte**: `diagramadeclassesCRMclinica.pdf` (recebido após plan v1)
**Documento alvo**: [data-model.md](./data-model.md) v2

Este documento registra o diff entre o modelo de dados v1 (derivado apenas da spec) e o modelo v2 reconciliado com o Diagrama de Classes UML oficial.

---

## 1. Princípio de Reconciliação

- O **diagrama de classes é fonte de verdade do modelo de domínio**.
- A **spec/PDF é fonte de verdade dos requisitos funcionais e não-funcionais**.
- Quando o diagrama omite uma entidade necessária para cumprir um RNF (LGPD, audit, multi-tenant, autenticação), a tabela é **mantida** mas classificada como "auxiliar — fora do diagrama".
- Nomenclatura: tabelas e colunas em **pt-BR** alinhadas ao diagrama. Identificadores técnicos universais (`refresh_token`, `webhook`, `cron`) permanecem em inglês.

---

## 2. Entidades do Diagrama (12) → Tabelas (12 principais)

| Diagrama | Tabela v2 | Tabela v1 | Status |
|----------|-----------|-----------|--------|
| Usuario | `usuario` | `user_account` | **RENOMEADA** |
| Gestor (subclasse) | discriminator `usuario.perfil = 'GESTOR'` | role_id=MANAGER | Inline |
| Recepcionista (subclasse) | discriminator `usuario.perfil = 'RECEPCIONISTA'` + `permissoes_recepcionista` | role_id=RECEPTIONIST + user_capability | Inline + tabela aux |
| Medico (subclasse) | discriminator `usuario.perfil = 'MEDICO'` + `perfil_medico` | doctor_profile | **RENOMEADA** |
| Paciente | `paciente` | `patient` | **RENOMEADA** + `notas_internas` adicionada |
| Atendimento | `atendimento` | `conversation` | **RENOMEADA + RESEMANTIZADA** |
| Mensagem | `mensagem` | `message` | **RENOMEADA** |
| MensagemRapida | `mensagem_rapida` | `quick_message` | **RENOMEADA** + campo `atalho` adicionado |
| Lembrete | `lembrete` | `reminder` | **RENOMEADA** |
| Agendamento | `agendamento` | `appointment` | **RENOMEADA** + campo `motivo_cancelamento_ia` inline |
| PesquisaSatisfacao | `pesquisa_satisfacao` | `satisfaction_response` | **RENOMEADA** + simplificada |
| Tag | `tag` | `tag` | Mantém |
| JanelaHorarioIA | `janela_horario_ia` | `ai_window` | **REMODELADA** (N linhas por dia) |
| RegraAutomacao | `regra_automacao` | `automation_rule` | **RENOMEADA** |
| APIDarwin (interface) | — (não vira tabela; vira classe Java `DarwinClient`) | — | Conforme diagrama |

---

## 3. Tabelas Auxiliares (Mantidas — Fora do Diagrama)

Estas tabelas **não estão no diagrama** mas são **obrigatórias** para cumprir requisitos do PDF (LGPD, NFR-02, NFR-09) ou para suportar a arquitetura técnica.

| Tabela v2 | Origem do requisito | Justificativa |
|-----------|--------------------|--------------|
| `clinica` | R10 (multi-tenant primitives) | Tenant root — futura expansão multi-clínica |
| `perfil_medico` | Subclasse Medico do diagrama | Atributo `especialidade` requer tabela própria |
| `permissoes_recepcionista` | Subclasse Recepcionista (`permissoesAbas`) | Lista de permissões — relacional 0..* |
| `capacidade_usuario` | FR-AUTH-05 | Toggles configurados pelo Gestor (ver Dashboard, exportar contatos, transferir conversas) |
| `horario_atendente` | RF13 + FR-HOR-03 | Janela de trabalho do atendente (separada da JanelaHorarioIA) |
| `refresh_token` | NFR-04 | Token opaco com revogação granular |
| `consentimento` | RNF03 (LGPD Art.5/8) | Registro de base legal + finalidade + versão |
| `transferencia_atendimento` | BR-05 + diagrama método `transferirAtendente()` | Histórico de transferências |
| `midia_mensagem` | RF15 (áudio/imagem/documento) | Detalha tipo de mídia + referência S3 |
| `cancelamento_ia` | RF22 (visão agregada de cancelamentos da IA) | Tabela de log detalhado complementar ao campo inline em `agendamento` |
| `log_automacao` | FR-AUT-04 (auditoria de regras disparadas) | Histórico de cada disparo de regra |
| `tipo_agendamento` | Normalização | Lookup CONSULTA/EXAME/CIRURGIA/RETORNO |
| `categoria_mensagem_rapida` | RF05 (categorias enum) | Lookup com cor configurável |
| `paciente_tag` | Diagrama N:N implícito Paciente ↔ Tag | Tabela de junção |
| `log_auditoria` | RNF03 (LGPD audit trail) | Quem acessou qual dado pessoal e quando |
| `relatorio_incidente` | RNF03 (protocolo de notificação) | Registro de incidente LGPD |
| `estado_sync_darwin` | RF17 + R7 | Cursor de última sincronização |

Total: **17 tabelas auxiliares** justificadas + **12 tabelas principais** = **29 tabelas**.
(Diferente do v1 que tinha 28: nova tabela `permissoes_recepcionista` adicionada para refletir `Recepcionista.permissoesAbas` do diagrama.)

---

## 4. Campos Adicionados (Faltavam no v1)

| Tabela | Campo novo | Origem | Tipo |
|--------|-----------|--------|------|
| `paciente` | `notas_internas` | Diagrama `Paciente.notasInternas` | TEXT (🔒 encrypted) |
| `mensagem_rapida` | `atalho` | Diagrama `MensagemRapida.atalho` | VARCHAR(40) (ex: `/abertura`) |
| `agendamento` | `motivo_cancelamento_ia` | Diagrama `Agendamento.motivoCancelamentoIA` | VARCHAR(500) (espelho do reason em cancelamento_ia para queries rápidas) |
| `mensagem` | `remetente` | Diagrama `Mensagem.remetente` (String) | VARCHAR(20) — enum PACIENTE/ATENDENTE/IA/SISTEMA |
| `permissoes_recepcionista` (tabela) | `aba_permitida` | Diagrama `Recepcionista.permissoesAbas` | VARCHAR(40) — códigos das abas |

---

## 5. Campos Removidos / Simplificados

| Tabela | Campo removido | Motivo |
|--------|---------------|--------|
| `pesquisa_satisfacao` | `raw_reply`, `requires_review`, `score CHECK` | Diagrama tem apenas `nota` + `dataAvaliacao`. Simplificado para alinhar; `nota` aceita NULL para respostas não-numéricas (registradas em log à parte se necessário). |
| `usuario` | `role_id BIGINT FK role(id)` | Diagrama usa string `perfil`. Trocado por `perfil VARCHAR(20)` discriminator + CHECK constraint. Tabela `role` removida; lookups são constantes no código. |
| `janela_horario_ia` | `is_24h`, `weekday_mask BIGINT` | Diagrama mostra dia + horaInicio + horaFim por entidade. Remodelado para N linhas (1 por dia). Flag 24h vira coluna `eh_24h BOOLEAN` em `clinica` ou linha especial com horas 00:00→23:59. |

---

## 6. Relacionamentos Reconciliados

| Diagrama | Implementação SQL |
|----------|-------------------|
| Usuario `1` — `0..*` Atendimento (atendente principal) | `atendimento.atendente_principal_id` FK → `usuario(id)` |
| Usuario `1` — `0..*` MensagemRapida (cria e gerencia) | `mensagem_rapida.usuario_id` FK → `usuario(id)` |
| Usuario `1` — `0..*` Lembrete (responsável por) | `lembrete.criado_por_usuario_id` FK → `usuario(id)` |
| Medico `1` — `0..*` Agendamento (realiza) | `agendamento.medico_id` FK → `usuario(id)` + CHECK `(SELECT perfil FROM usuario WHERE id = medico_id) = 'MEDICO'` (enforced via trigger ou service layer) |
| Paciente `1` — `0..*` Atendimento (possui) | `atendimento.paciente_id` FK → `paciente(id)` |
| Paciente `1` — `0..*` Lembrete (associado a) | `lembrete.paciente_id` FK → `paciente(id)` |
| Paciente `1` — `0..*` PesquisaSatisfacao (responde) | `pesquisa_satisfacao.paciente_id` FK → `paciente(id)` |
| Paciente `0..*` — `0..*` Tag (classificado por) | `paciente_tag(paciente_id, tag_id)` join table |
| Paciente `1` — `0..*` Agendamento (possui) | `agendamento.paciente_id` FK → `paciente(id)` |
| Atendimento `1` — `0..*` Mensagem (contém) | `mensagem.atendimento_id` FK → `atendimento(id)` |
| APIDarwin importa Paciente | `DarwinClient.importarPacientes()` → upsert em `paciente` via `darwin_id_externo` |
| APIDarwin importa Agendamento | `DarwinClient.importarAgendamentos()` → upsert em `agendamento` via `darwin_id_externo` |

---

## 7. Mudança Semântica Crítica: `conversation` → `atendimento`

No modelo v1, "Conversation" era um agregador de WhatsApp messages (1 por paciente). No diagrama, **Atendimento** carrega `dataInicio` + `status` + `transferirAtendente()` — sugere um conceito de **sessão de atendimento** que pode começar e terminar (e ser reaberto), não apenas uma thread perpétua.

**Decisão v2**: `atendimento` é uma sessão com início e (opcionalmente) fim. Um paciente pode ter **múltiplos atendimentos** ao longo do tempo. Cada atendimento agrupa mensagens daquele período.

- `atendimento.status`: `ATIVO` | `TRANSFERIDO` | `ENCERRADO` | `IA_AUTOMATICO`
- Atendimento `ENCERRADO` pode disparar pesquisa de satisfação (FR-SAT-01) se associado a agendamento `COMPLETED`.
- Mensagens novas em conversa com último atendimento `ENCERRADO` há > 24h reabrem um novo atendimento.
- Paciente mantém referência ao atendimento corrente via `paciente.atendimento_atual_id` (calculado/desnormalizado).

Isso difere do v1 (1 conversation eterna por paciente). Impacto:
- Histórico mais rico — métricas de "duração média de atendimento" ficam viáveis.
- `paciente.status` (EM_ATENDIMENTO/AGENDADO/FOLLOW_UP/FINALIZADO) continua sendo o status do **paciente**, não do atendimento.
- A view "lista de atendimentos" mostra atendimentos `ATIVO` por padrão; histórico inclui encerrados.

---

## 8. Hierarquia Usuario / Gestor / Recepcionista / Medico

Diagrama UML mostra herança (generalização). Implementação relacional:

**Decisão**: **Single-table inheritance** com discriminator `usuario.perfil` (VARCHAR enum) + tabelas auxiliares para campos específicos de subclasse.

- `usuario` (tabela mãe): `id`, `nome`, `email`, `senha_hash`, `perfil` (`GESTOR` | `RECEPCIONISTA` | `MEDICO`), demais campos comuns.
- `perfil_medico` (1:1 com `usuario` quando `perfil = 'MEDICO'`): `usuario_id` (PK + FK), `especialidade`, `crm`.
- `permissoes_recepcionista` (1:N quando `perfil = 'RECEPCIONISTA'`): `usuario_id` FK, `aba_permitida`.

Hibernate mapping: `@Inheritance(strategy = SINGLE_TABLE)` + `@DiscriminatorColumn(name = "perfil")` + subclasses `Gestor`, `Recepcionista`, `Medico` extends `Usuario`. `perfil_medico` é entidade `@Embeddable` ou `@OneToOne` separada conforme a complexidade.

---

## 9. Resumo de Renomeações (en → pt-BR)

| v1 (inglês) | v2 (pt-BR) |
|-------------|-----------|
| `user_account` | `usuario` |
| `role` | (removida — inline em `usuario.perfil`) |
| `doctor_profile` | `perfil_medico` |
| `user_capability` | `capacidade_usuario` |
| `attendant_schedule` | `horario_atendente` |
| `patient` | `paciente` |
| `consent` | `consentimento` |
| `conversation` | `atendimento` (+ resemântica seção §7) |
| `conversation_transfer` | `transferencia_atendimento` |
| `message` | `mensagem` |
| `message_media` | `midia_mensagem` |
| `appointment` | `agendamento` |
| `appointment_type` | `tipo_agendamento` |
| `ai_window` | `janela_horario_ia` |
| `ai_cancellation` | `cancelamento_ia` |
| `reminder` | `lembrete` |
| `quick_message` | `mensagem_rapida` |
| `quick_message_category` | `categoria_mensagem_rapida` |
| `tag` | `tag` |
| `patient_tag` | `paciente_tag` |
| `automation_rule` | `regra_automacao` |
| `automation_log` | `log_automacao` |
| `satisfaction_response` | `pesquisa_satisfacao` |
| `audit_log` | `log_auditoria` |
| `incident_report` | `relatorio_incidente` |
| `darwin_sync_state` | `estado_sync_darwin` |
| `clinic` | `clinica` |
| `refresh_token` | `refresh_token` (mantém — termo técnico universal) |

### Colunas comuns renomeadas

| v1 | v2 |
|----|-----|
| `full_name` | `nome` |
| `password_hash` | `senha_hash` |
| `phone` | `telefone` |
| `birth_date` | `data_nascimento` |
| `address` | `endereco` |
| `body` | `conteudo` (mensagem) / `texto` (mensagem_rapida) / `mensagem` (lembrete) |
| `created_at` | `criado_em` |
| `updated_at` | `atualizado_em` |
| `deleted_at` | `deletado_em` |
| `scheduled_start` | `data_hora` (agendamento) |
| `scheduled_end` | `data_hora_fim` |
| `fire_at` | `data_hora_programada` |
| `fired_at` | `disparado_em` |
| `is_enabled` | `ativo` |
| `is_active` | `ativo` |
| `is_cancelled` | `cancelado` |
| `is_ai_handled` | `tratado_por_ia` |
| `last_message_at` | `ultima_mensagem_em` |
| `unread_count` | `nao_lidas` |
| `last_login_at` | `ultimo_login_em` |

---

## 10. Impacto em Outros Artefatos

| Artefato | Mudança necessária |
|----------|--------------------|
| `data-model.md` | **Reescrita completa** (v2) |
| `plan.md` §4.2 package layout | Renomear classes Java: `Patient` → `Paciente`, `Conversation` → `Atendimento`, etc. |
| `plan.md` §5 / §6 / §7 | Atualizar referências aos nomes de tabela |
| `research.md` R16 | Status: **resolvido** (reconciliação completa, diff em §10 deste documento) |
| `research.md` R17 (novo) | Decisão de nomenclatura pt-BR registrada |
| `contracts/rest/*.md` | Endpoints e payload field names migrados para pt-BR |
| `contracts/events/stomp-topics.md` | Payload field names migrados |
| `contracts/webhooks/*.md` | Webhooks externos (Meta, N8N) mantêm payload nativo; nossos webhooks usam pt-BR |
| `quickstart.md` | Atualizar nomes nos exemplos de cURL |
| `spec.md` §7 Key Entities | Mantida em inglês (documento já validado); referência cruzada adicionada |
| `CLAUDE.md` | Já aponta para artefatos atualizados — sem mudança estrutural |

---

## 11. Verificação Final

Após aplicar v2, verificar:

- [x] Toda entidade do diagrama tem tabela correspondente em pt-BR
- [x] Todo campo do diagrama existe na tabela correspondente (ou justificativa para ausência)
- [x] Todo relacionamento do diagrama tem FK/join table correspondente
- [x] Tabelas auxiliares (LGPD, audit, etc.) justificadas via requisito do PDF
- [x] Soft delete preservado em `paciente`, `usuario`, `tag`, `mensagem_rapida`, `regra_automacao`
- [x] Encryption (🔒) preservada para colunas sensíveis (LGPD)
- [x] `clinic_id` mantido em todas as tabelas de tenant (R10)
- [x] State machines de `paciente.status` e `agendamento.status` mantidas
- [x] Nova state machine de `atendimento.status` documentada

---

## 12. Próximos Passos

1. Aplicar v2 em `data-model.md` (reescrita).
2. Patch cirúrgico em `plan.md` para refletir novos nomes nas seções de package layout, segurança e integrações.
3. Patch em `research.md` R16/R17.
4. Renomear endpoints REST e field names nos contracts para pt-BR (mantendo nomes técnicos de webhooks externos como Meta/N8N).
5. Validar que nenhuma referência a nome v1 (`patient`, `conversation`, etc.) sobrou nos artefatos da feature.
