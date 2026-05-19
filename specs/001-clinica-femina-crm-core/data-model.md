# Fase 1 — Modelo de Dados (v2 — Reconciliado com Diagrama de Classes)

**Feature**: 001-clinica-femina-crm-core
**Banco**: PostgreSQL 16
**Ferramenta de migração**: Flyway
**Versão**: v2 (alinhada ao Diagrama de Classes UML — ver [data-model-reconciliation.md](./data-model-reconciliation.md))
**Convenções**:
- Nomenclatura: `snake_case` em **pt-BR**, alinhada ao Diagrama de Classes.
- PK: sempre `id BIGSERIAL`.
- FK: nomeada `<tabela_referenciada>_id`.
- Soft-delete: `deletado_em TIMESTAMPTZ NULL`.
- Auditoria de linha: `criado_em`, `atualizado_em`, opcionais `criado_por`, `atualizado_por`.
- 🔒 = coluna criptografada em repouso (AES-256-GCM via `AesGcmConverter`).
- Tenant: `clinica_id BIGINT NOT NULL REFERENCES clinica(id)` nas tabelas tenant-bearing (R10).

---

## 1. Visão Geral das Entidades

### Entidades Principais (do Diagrama — 12)

| # | Tabela | Diagrama | Soft-delete | Colunas 🔒 |
|---|--------|----------|-------------|-----------|
| 1 | `usuario` | Usuario | Sim | senha_hash (BCrypt — não é AES) |
| 2 | `perfil_medico` | Medico (subclasse) | Não | — |
| 3 | `paciente` | Paciente | Sim | nome, cpf, data_nascimento, email, telefone, endereco, notas_internas |
| 4 | `atendimento` | Atendimento | Não | — |
| 5 | `mensagem` | Mensagem | Não | conteudo |
| 6 | `mensagem_rapida` | MensagemRapida | Sim | texto |
| 7 | `lembrete` | Lembrete | Não | mensagem |
| 8 | `agendamento` | Agendamento | Não | notas_clinicas |
| 9 | `pesquisa_satisfacao` | PesquisaSatisfacao | Não | resposta_livre |
| 10 | `tag` | Tag | Sim | — |
| 11 | `janela_horario_ia` | JanelaHorarioIA | Não | — |
| 12 | `regra_automacao` | RegraAutomacao | Sim | template_mensagem |

### Tabelas Auxiliares (Fora do Diagrama — Necessárias para RNF/Arquitetura)

| # | Tabela | Origem do requisito |
|---|--------|--------------------|
| 13 | `clinica` | R10 — primitives multi-tenant |
| 14 | `permissoes_recepcionista` | Diagrama Recepcionista.permissoesAbas |
| 15 | `capacidade_usuario` | FR-AUTH-05 |
| 16 | `horario_atendente` | RF13 + FR-HOR-03 |
| 17 | `refresh_token` | NFR-04 |
| 18 | `consentimento` | RNF03 (LGPD Art.5/8) |
| 19 | `transferencia_atendimento` | BR-05 + método `transferirAtendente()` |
| 20 | `midia_mensagem` | RF15 (áudios/imagens/documentos) |
| 21 | `tipo_agendamento` | Normalização (CONSULTA/EXAME/CIRURGIA/RETORNO) |
| 22 | `categoria_mensagem_rapida` | RF05 (categorias) |
| 23 | `paciente_tag` | Junção N:N |
| 24 | `cancelamento_ia` | RF22 (log detalhado) |
| 25 | `log_automacao` | FR-AUT-04 |
| 26 | `log_auditoria` | RNF03 (audit trail LGPD) |
| 27 | `relatorio_incidente` | RNF03 (protocolo incidente) |
| 28 | `estado_sync_darwin` | RF17 |
| 29 | `whatsapp_payload_log` | Replay/debug Meta webhooks |

**Total**: 29 tabelas (12 do diagrama + 17 auxiliares justificadas).

---

## 2. Tabelas Lookup / Raiz

### 2.1 `clinica`

```sql
CREATE TABLE clinica (
    id              BIGSERIAL PRIMARY KEY,
    nome            VARCHAR(200) NOT NULL,
    razao_social    VARCHAR(200) NOT NULL,
    cnpj            VARCHAR(18)  NOT NULL UNIQUE,
    email_contato   VARCHAR(255) NOT NULL,
    telefone_contato VARCHAR(20) NOT NULL,
    logo_url        VARCHAR(500),
    cor_primaria    VARCHAR(7),         -- hex
    tema_padrao     VARCHAR(10) DEFAULT 'CLARO',
    fuso_horario    VARCHAR(60) NOT NULL DEFAULT 'America/Sao_Paulo',
    ia_24h          BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 2.2 `tipo_agendamento`

```sql
CREATE TABLE tipo_agendamento (
    id      SMALLSERIAL PRIMARY KEY,
    codigo  VARCHAR(30) NOT NULL UNIQUE,    -- CONSULTA, EXAME, CIRURGIA, RETORNO
    rotulo  VARCHAR(80) NOT NULL
);

INSERT INTO tipo_agendamento (codigo, rotulo) VALUES
  ('CONSULTA',  'Consulta'),
  ('EXAME',     'Exame'),
  ('CIRURGIA',  'Cirurgia'),
  ('RETORNO',   'Retorno');
```

### 2.3 `categoria_mensagem_rapida`

```sql
CREATE TABLE categoria_mensagem_rapida (
    id      SMALLSERIAL PRIMARY KEY,
    codigo  VARCHAR(30) NOT NULL UNIQUE,
    rotulo  VARCHAR(80) NOT NULL,
    cor     VARCHAR(7)
);

INSERT INTO categoria_mensagem_rapida (codigo, rotulo, cor) VALUES
  ('ABERTURA',     'Abertura',     '#3B82F6'),
  ('AGENDAMENTO',  'Agendamento',  '#10B981'),
  ('ORCAMENTO',    'Orçamento',    '#F59E0B'),
  ('SUPORTE',      'Suporte',      '#8B5CF6'),
  ('FINANCEIRO',   'Financeiro',   '#EF4444'),
  ('ENCERRAMENTO', 'Encerramento', '#EC4899'),
  ('CIRURGIA',     'Cirurgia',     '#06B6D4'),
  ('ORIENTACOES',  'Orientações',  '#84CC16');
```

---

## 3. Identidade & Acesso

### 3.1 `usuario` (Diagrama: **Usuario** + subclasses Gestor/Recepcionista/Medico)

```sql
CREATE TABLE usuario (
    id              BIGSERIAL PRIMARY KEY,
    clinica_id      BIGINT NOT NULL REFERENCES clinica(id),
    nome            VARCHAR(200) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    telefone        VARCHAR(20),
    senha_hash      VARCHAR(72) NOT NULL,                     -- BCrypt custo 12
    perfil          VARCHAR(20) NOT NULL,                     -- discriminator: GESTOR | RECEPCIONISTA | MEDICO
    foto_url        VARCHAR(500),
    tema_preferencia VARCHAR(10) DEFAULT 'CLARO',
    ativo           BOOLEAN NOT NULL DEFAULT TRUE,
    ultimo_login_em TIMESTAMPTZ,
    deletado_em     TIMESTAMPTZ,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_usuario_perfil CHECK (perfil IN ('GESTOR','RECEPCIONISTA','MEDICO')),
    UNIQUE (clinica_id, email)
);

CREATE INDEX idx_usuario_clinica_perfil ON usuario(clinica_id, perfil) WHERE deletado_em IS NULL;
```

**Hibernate mapping**:
```java
@Entity
@Table(name = "usuario")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "perfil", discriminatorType = DiscriminatorType.STRING)
public abstract class Usuario { ... }

@Entity @DiscriminatorValue("GESTOR")        public class Gestor extends Usuario { }
@Entity @DiscriminatorValue("RECEPCIONISTA") public class Recepcionista extends Usuario { }
@Entity @DiscriminatorValue("MEDICO")        public class Medico extends Usuario { }
```

### 3.2 `perfil_medico` (Diagrama: **Medico.especialidade**)

```sql
CREATE TABLE perfil_medico (
    usuario_id      BIGINT PRIMARY KEY REFERENCES usuario(id) ON DELETE CASCADE,
    especialidade   VARCHAR(50) NOT NULL,        -- OBSTETRICIA_PRE_NATAL | GINECOLOGIA | ULTRASSONOGRAFIA | CLINICA_GERAL
    crm             VARCHAR(20) NOT NULL,
    bio             TEXT,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_perfil_medico_especialidade CHECK (
        especialidade IN ('OBSTETRICIA_PRE_NATAL','GINECOLOGIA','ULTRASSONOGRAFIA','CLINICA_GERAL')
    )
);
```

### 3.3 `permissoes_recepcionista` (Diagrama: **Recepcionista.permissoesAbas**)

```sql
CREATE TABLE permissoes_recepcionista (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    aba_permitida   VARCHAR(40) NOT NULL,        -- ATENDIMENTOS, DASHBOARD, AGENDA, PACIENTES, EQUIPE, TAGS, MSGS_RAPIDAS, CONFIGURACOES, AUTOMACAO, HORARIOS, SATISFACAO, CANCELAMENTOS_IA
    UNIQUE (usuario_id, aba_permitida)
);
```

### 3.4 `capacidade_usuario` (FR-AUTH-05)

```sql
CREATE TABLE capacidade_usuario (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    capacidade      VARCHAR(50) NOT NULL,        -- VIEW_DASHBOARD | EXPORT_CONTACTS | TRANSFER_CONVERSATIONS
    concedida_por   BIGINT REFERENCES usuario(id),
    concedida_em    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (usuario_id, capacidade)
);
```

### 3.5 `horario_atendente` (RF13 + FR-HOR-03)

```sql
CREATE TABLE horario_atendente (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    dia_semana      SMALLINT NOT NULL CHECK (dia_semana BETWEEN 0 AND 6),    -- 0=Domingo
    hora_inicio     TIME NOT NULL,
    hora_fim        TIME NOT NULL,
    ativo           BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (hora_inicio < hora_fim)
);

CREATE INDEX idx_horario_atendente_usuario ON horario_atendente(usuario_id, dia_semana);
```

### 3.6 `refresh_token`

```sql
CREATE TABLE refresh_token (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL UNIQUE,           -- SHA-256 do token opaco
    emitido_em      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expira_em       TIMESTAMPTZ NOT NULL,
    revogado_em     TIMESTAMPTZ,
    user_agent      VARCHAR(500),
    ip_address      INET
);

CREATE INDEX idx_refresh_token_usuario ON refresh_token(usuario_id) WHERE revogado_em IS NULL;
```

---

## 4. Domínio do Paciente

### 4.1 `paciente` (Diagrama: **Paciente**)

```sql
CREATE TABLE paciente (
    id                          BIGSERIAL PRIMARY KEY,
    clinica_id                  BIGINT NOT NULL REFERENCES clinica(id),
    nome                        BYTEA NOT NULL,                  -- 🔒
    nome_busca                  VARCHAR(200) NOT NULL,           -- lowercase normalizado para pg_trgm
    cpf                         BYTEA UNIQUE,                    -- 🔒
    cpf_hash                    CHAR(64) UNIQUE,                 -- SHA-256 com pepper
    data_nascimento             BYTEA,                           -- 🔒
    email                       BYTEA,                           -- 🔒
    email_hash                  CHAR(64),
    telefone                    BYTEA NOT NULL,                  -- 🔒
    telefone_normalizado        VARCHAR(20) NOT NULL,            -- E.164 para matching/busca
    endereco                    BYTEA,                           -- 🔒 JSONB serializado
    horario_preferencial        VARCHAR(20),                     -- MANHA | TARDE | NOITE
    notas_internas              BYTEA,                           -- 🔒 (Diagrama: notasInternas)
    status                      VARCHAR(20) NOT NULL DEFAULT 'EM_ATENDIMENTO',
    follow_up_ativo             BOOLEAN NOT NULL DEFAULT FALSE,
    atendente_principal_id      BIGINT REFERENCES usuario(id),
    medico_principal_id         BIGINT REFERENCES usuario(id),
    atendimento_atual_id        BIGINT,                          -- FK adicionado após criação de atendimento
    valor_total                 NUMERIC(12,2) DEFAULT 0,
    chave_criptografia_id       VARCHAR(20) NOT NULL DEFAULT 'v1',
    darwin_id_externo           VARCHAR(100) UNIQUE,
    darwin_dados_importados     JSONB,
    requer_revisao              BOOLEAN NOT NULL DEFAULT FALSE,
    ultima_interacao_em         TIMESTAMPTZ,
    deletado_em                 TIMESTAMPTZ,
    criado_em                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    criado_por                  BIGINT REFERENCES usuario(id),
    atualizado_por              BIGINT REFERENCES usuario(id),
    CONSTRAINT chk_paciente_status CHECK (status IN ('EM_ATENDIMENTO','AGENDADO','FINALIZADO'))
);

CREATE INDEX idx_paciente_clinica_status     ON paciente(clinica_id, status) WHERE deletado_em IS NULL;
CREATE INDEX idx_paciente_atendente          ON paciente(atendente_principal_id) WHERE deletado_em IS NULL;
CREATE INDEX idx_paciente_medico             ON paciente(medico_principal_id) WHERE deletado_em IS NULL;
CREATE INDEX idx_paciente_telefone_norm      ON paciente(telefone_normalizado) WHERE deletado_em IS NULL;
CREATE INDEX idx_paciente_nome_trgm          ON paciente USING GIN (nome_busca gin_trgm_ops) WHERE deletado_em IS NULL;
CREATE INDEX idx_paciente_ultima_interacao   ON paciente(ultima_interacao_em) WHERE deletado_em IS NULL;
```

**Notas**:
- `status` segue máquina de estados linear `EM_ATENDIMENTO → AGENDADO → FINALIZADO` (BR-01). `follow_up_ativo` é flag paralelo.
- Soft-delete via Hibernate `@SQLRestriction("deletado_em IS NULL")` (R12).
- Hard delete só via `LgpdController.removerPacienteDefinitivo(id, motivo)` — exige Gestor + log de auditoria.

### 4.2 `consentimento` (LGPD)

```sql
CREATE TABLE consentimento (
    id                      BIGSERIAL PRIMARY KEY,
    paciente_id             BIGINT NOT NULL REFERENCES paciente(id),
    base_legal              VARCHAR(50) NOT NULL,        -- CONSENTIMENTO | INTERESSE_LEGITIMO | INTERESSE_VITAL | ...
    finalidade              VARCHAR(100) NOT NULL,       -- ATENDIMENTO | LEMBRETES | MARKETING | FIDELIZACAO
    versao_texto_consentimento VARCHAR(20) NOT NULL,
    consentimento_dado      BOOLEAN NOT NULL,
    concedido_em            TIMESTAMPTZ NOT NULL,
    concedido_via           VARCHAR(20) NOT NULL,        -- WHATSAPP | PRESENCIAL | WEB
    revogado_em             TIMESTAMPTZ,
    evidencia_bruta         JSONB,
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_consentimento_paciente_finalidade ON consentimento(paciente_id, finalidade);
```

---

## 5. Atendimento, Mensageria

### 5.1 `atendimento` (Diagrama: **Atendimento** — dataInicio, status, transferirAtendente())

```sql
CREATE TABLE atendimento (
    id                          BIGSERIAL PRIMARY KEY,
    clinica_id                  BIGINT NOT NULL REFERENCES clinica(id),
    paciente_id                 BIGINT NOT NULL REFERENCES paciente(id),
    atendente_principal_id      BIGINT REFERENCES usuario(id),
    data_inicio                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),     -- Diagrama
    data_encerramento           TIMESTAMPTZ,
    status                      VARCHAR(20) NOT NULL DEFAULT 'ATIVO',   -- Diagrama
    tratado_por_ia              BOOLEAN NOT NULL DEFAULT FALSE,
    motivo_encerramento         VARCHAR(255),
    ultima_mensagem_em          TIMESTAMPTZ,
    nao_lidas                   INTEGER NOT NULL DEFAULT 0,
    whatsapp_chat_id            VARCHAR(50),
    criado_em                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_atendimento_status CHECK (status IN ('ATIVO','TRANSFERIDO','ENCERRADO','IA_AUTOMATICO'))
);

CREATE INDEX idx_atendimento_paciente              ON atendimento(paciente_id, data_inicio DESC);
CREATE INDEX idx_atendimento_atendente_recente     ON atendimento(atendente_principal_id, ultima_mensagem_em DESC) WHERE status IN ('ATIVO','IA_AUTOMATICO');
CREATE INDEX idx_atendimento_ia                    ON atendimento(tratado_por_ia, ultima_mensagem_em DESC);
CREATE INDEX idx_atendimento_status_ultima_msg     ON atendimento(status, ultima_mensagem_em DESC);

-- FK circular adicionada após criação das duas tabelas:
ALTER TABLE paciente ADD CONSTRAINT fk_paciente_atendimento_atual FOREIGN KEY (atendimento_atual_id) REFERENCES atendimento(id) DEFERRABLE INITIALLY DEFERRED;
```

**Máquina de estados de `status`**:
```
ATIVO ───► TRANSFERIDO ───► ATIVO (no novo atendente)
ATIVO ───► IA_AUTOMATICO ───► ATIVO (handoff de volta para humano)
ATIVO ───► ENCERRADO
```

- Nova mensagem inbound em conversa com último atendimento `ENCERRADO` há > 24h cria um novo `atendimento` (sessão).
- Método `transferirAtendente()` do diagrama é implementado em `AtendimentoService.transferirAtendente(atendimentoId, novoUsuarioId, motivo)` — cria linha em `transferencia_atendimento` e atualiza `atendente_principal_id`.

### 5.2 `transferencia_atendimento` (BR-05)

```sql
CREATE TABLE transferencia_atendimento (
    id                  BIGSERIAL PRIMARY KEY,
    atendimento_id      BIGINT NOT NULL REFERENCES atendimento(id) ON DELETE CASCADE,
    de_usuario_id       BIGINT REFERENCES usuario(id),
    para_usuario_id     BIGINT NOT NULL REFERENCES usuario(id),
    transferido_por     BIGINT NOT NULL REFERENCES usuario(id),
    motivo              VARCHAR(500),
    transferido_em      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transferencia_atendimento ON transferencia_atendimento(atendimento_id, transferido_em DESC);
```

### 5.3 `mensagem` (Diagrama: **Mensagem**)

```sql
CREATE TABLE mensagem (
    id                      BIGSERIAL PRIMARY KEY,
    atendimento_id          BIGINT NOT NULL REFERENCES atendimento(id) ON DELETE CASCADE,
    direcao                 VARCHAR(10) NOT NULL,                -- ENTRADA | SAIDA
    remetente               VARCHAR(20) NOT NULL,                -- PACIENTE | ATENDENTE | IA | SISTEMA  (Diagrama)
    remetente_usuario_id    BIGINT REFERENCES usuario(id),       -- preenchido quando remetente = ATENDENTE
    tipo_media              VARCHAR(20) NOT NULL,                -- TEXTO | AUDIO | IMAGEM | DOCUMENTO | TEMPLATE  (Diagrama)
    conteudo                BYTEA,                               -- 🔒 (Diagrama: conteudo)
    conteudo_previa         VARCHAR(60),                         -- excerpt não-sensível
    data_hora               TIMESTAMPTZ NOT NULL DEFAULT NOW(),  -- Diagrama
    whatsapp_message_id     VARCHAR(100) UNIQUE,
    whatsapp_status         VARCHAR(20),                         -- ENVIADA | ENTREGUE | LIDA | FALHA
    mensagem_rapida_id      BIGINT REFERENCES mensagem_rapida(id),
    entregue_em             TIMESTAMPTZ,
    lida_em                 TIMESTAMPTZ,
    motivo_falha            VARCHAR(255),
    chave_criptografia_id   VARCHAR(20) NOT NULL DEFAULT 'v1',
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_mensagem_remetente CHECK (remetente IN ('PACIENTE','ATENDENTE','IA','SISTEMA')),
    CONSTRAINT chk_mensagem_tipo_media CHECK (tipo_media IN ('TEXTO','AUDIO','IMAGEM','DOCUMENTO','TEMPLATE')),
    CONSTRAINT chk_mensagem_direcao CHECK (direcao IN ('ENTRADA','SAIDA'))
);

CREATE INDEX idx_mensagem_atendimento_tempo ON mensagem(atendimento_id, data_hora DESC);
CREATE INDEX idx_mensagem_nao_lida          ON mensagem(atendimento_id) WHERE lida_em IS NULL AND direcao = 'ENTRADA';
```

### 5.4 `midia_mensagem`

```sql
CREATE TABLE midia_mensagem (
    id                  BIGSERIAL PRIMARY KEY,
    mensagem_id         BIGINT NOT NULL REFERENCES mensagem(id) ON DELETE CASCADE,
    tipo_media          VARCHAR(20) NOT NULL,        -- AUDIO | IMAGEM | DOCUMENTO
    mime_type           VARCHAR(120) NOT NULL,
    s3_bucket           VARCHAR(100) NOT NULL,
    s3_chave            BYTEA NOT NULL,              -- 🔒
    tamanho_bytes       BIGINT NOT NULL,
    duracao_segundos    INTEGER,                     -- para áudio
    whatsapp_media_id   VARCHAR(100),
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 6. Agendamento

### 6.1 `agendamento` (Diagrama: **Agendamento**)

```sql
CREATE TABLE agendamento (
    id                          BIGSERIAL PRIMARY KEY,
    clinica_id                  BIGINT NOT NULL REFERENCES clinica(id),
    paciente_id                 BIGINT NOT NULL REFERENCES paciente(id),
    medico_id                   BIGINT NOT NULL REFERENCES usuario(id),
    tipo_agendamento_id         SMALLINT NOT NULL REFERENCES tipo_agendamento(id),
    tipo_servico                VARCHAR(40) NOT NULL,                    -- Diagrama (string redundante para acelerar leitura)
    data_hora                   TIMESTAMPTZ NOT NULL,                    -- Diagrama
    data_hora_fim               TIMESTAMPTZ NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'AGENDADO', -- Diagrama
    origem                      VARCHAR(10) NOT NULL,                    -- HUMANO | IA | DARWIN
    agendado_por_usuario_id     BIGINT REFERENCES usuario(id),
    notas_clinicas              BYTEA,                                   -- 🔒
    confirmado_em               TIMESTAMPTZ,
    concluido_em                TIMESTAMPTZ,
    cancelado_em                TIMESTAMPTZ,
    cancelado_por_ia            BOOLEAN NOT NULL DEFAULT FALSE,
    motivo_cancelamento_ia      VARCHAR(500),                            -- Diagrama (espelho de cancelamento_ia.motivo p/ query rápida)
    no_show_em                  TIMESTAMPTZ,
    valor                       NUMERIC(10,2),
    darwin_id_externo           VARCHAR(100) UNIQUE,
    criado_em                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (data_hora < data_hora_fim),
    CONSTRAINT chk_agendamento_status CHECK (status IN ('AGENDADO','CONFIRMADO','CONCLUIDO','NO_SHOW','CANCELADO')),
    CONSTRAINT chk_agendamento_origem CHECK (origem IN ('HUMANO','IA','DARWIN'))
);

CREATE INDEX idx_agendamento_medico_semana  ON agendamento(medico_id, data_hora);
CREATE INDEX idx_agendamento_paciente       ON agendamento(paciente_id, data_hora DESC);
CREATE INDEX idx_agendamento_status_tempo   ON agendamento(status, data_hora);
CREATE INDEX idx_agendamento_clinica_dia    ON agendamento(clinica_id, data_hora);
```

**Validação por trigger** (R11 — defense in depth):
- `medico_id` deve referenciar um usuário com `perfil = 'MEDICO'` — verificado em service layer + check via subquery em trigger BEFORE INSERT/UPDATE.

### 6.2 `cancelamento_ia` (RF22 — log detalhado)

```sql
CREATE TABLE cancelamento_ia (
    id                  BIGSERIAL PRIMARY KEY,
    agendamento_id      BIGINT NOT NULL REFERENCES agendamento(id),
    atendimento_id      BIGINT REFERENCES atendimento(id),
    motivo              BYTEA NOT NULL,                  -- 🔒
    codigo_motivo       VARCHAR(50),                     -- PEDIDO_PACIENTE | SEM_DISPONIBILIDADE | ...
    cancelado_em        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    payload_bruto       JSONB                            -- N8N execution id, model ids
);

CREATE INDEX idx_cancelamento_ia_agendamento ON cancelamento_ia(agendamento_id);
CREATE INDEX idx_cancelamento_ia_tempo       ON cancelamento_ia(cancelado_em);
```

### 6.3 `janela_horario_ia` (Diagrama: **JanelaHorarioIA** — N linhas, uma por dia)

```sql
CREATE TABLE janela_horario_ia (
    id              BIGSERIAL PRIMARY KEY,
    clinica_id      BIGINT NOT NULL REFERENCES clinica(id),
    dia_semana      VARCHAR(10) NOT NULL,        -- Diagrama: DOMINGO | SEGUNDA | ... | SABADO
    hora_inicio     TIME NOT NULL,               -- Diagrama
    hora_fim        TIME NOT NULL,               -- Diagrama
    ativo           BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_janela_dia_semana CHECK (dia_semana IN ('DOMINGO','SEGUNDA','TERCA','QUARTA','QUINTA','SEXTA','SABADO')),
    CHECK (hora_inicio < hora_fim),
    UNIQUE (clinica_id, dia_semana)
);
```

**Flag 24h**: armazenada em `clinica.ia_24h BOOLEAN`. Quando `TRUE`, validação de janela é bypassada — IA pode agendar em qualquer momento. Quando `FALSE`, IA só pode agendar se houver linha em `janela_horario_ia` cujo dia/hora cubra o horário solicitado.

---

## 7. Operacional

### 7.1 `lembrete` (Diagrama: **Lembrete**)

```sql
CREATE TABLE lembrete (
    id                          BIGSERIAL PRIMARY KEY,
    clinica_id                  BIGINT NOT NULL REFERENCES clinica(id),
    paciente_id                 BIGINT NOT NULL REFERENCES paciente(id),
    criado_por_usuario_id       BIGINT NOT NULL REFERENCES usuario(id),
    data_hora_programada        TIMESTAMPTZ NOT NULL,         -- Diagrama
    mensagem                    BYTEA NOT NULL,               -- 🔒 (Diagrama: mensagem)
    disparado_em                TIMESTAMPTZ,
    dispensado_em               TIMESTAMPTZ,
    cancelado                   BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lembrete_devido    ON lembrete(data_hora_programada) WHERE disparado_em IS NULL AND cancelado = FALSE;
CREATE INDEX idx_lembrete_paciente  ON lembrete(paciente_id) WHERE cancelado = FALSE;
```

**Herança em transferência (BR-08)**: Lembretes referenciam o **paciente**, não o atendente. Ao firing, o `ReminderScheduler` resolve o atendente atual via `paciente.atendente_principal_id`.

Método `dispararNotificacao()` do diagrama é implementado em `LembreteScheduler` (Quartz cron 1 min) + `RealtimeBroadcastService` (STOMP push).

### 7.2 `tag` + `paciente_tag`

```sql
CREATE TABLE tag (
    id              BIGSERIAL PRIMARY KEY,
    clinica_id      BIGINT NOT NULL REFERENCES clinica(id),
    nome            VARCHAR(60) NOT NULL,       -- Diagrama
    cor             VARCHAR(7) NOT NULL,        -- Diagrama (hex)
    deletado_em     TIMESTAMPTZ,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    criado_por      BIGINT REFERENCES usuario(id),
    UNIQUE (clinica_id, nome) WHERE deletado_em IS NULL
);

CREATE TABLE paciente_tag (
    paciente_id     BIGINT NOT NULL REFERENCES paciente(id) ON DELETE CASCADE,
    tag_id          BIGINT NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    aplicada_por    BIGINT REFERENCES usuario(id),
    aplicada_em     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (paciente_id, tag_id)
);

CREATE INDEX idx_paciente_tag_tag ON paciente_tag(tag_id);
```

### 7.3 `mensagem_rapida` (Diagrama: **MensagemRapida** — com `atalho`)

```sql
CREATE TABLE mensagem_rapida (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    categoria_id    SMALLINT NOT NULL REFERENCES categoria_mensagem_rapida(id),
    titulo          VARCHAR(120) NOT NULL,
    atalho          VARCHAR(40) NOT NULL,       -- Diagrama: ex "/abertura", "/orcamento"
    texto           BYTEA NOT NULL,             -- 🔒 (Diagrama: texto)
    usos            INTEGER NOT NULL DEFAULT 0,
    deletado_em     TIMESTAMPTZ,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (usuario_id, atalho) WHERE deletado_em IS NULL
);

CREATE INDEX idx_mensagem_rapida_usuario_cat ON mensagem_rapida(usuario_id, categoria_id) WHERE deletado_em IS NULL;
```

**Privacidade por usuário (BR-04)**: Todo query em `MensagemRapidaRepository` inclui `usuario_id = :currentUserId`. Sem endpoint cross-user.

### 7.4 `regra_automacao` + `log_automacao`

```sql
CREATE TABLE regra_automacao (
    id                  BIGSERIAL PRIMARY KEY,
    clinica_id          BIGINT NOT NULL REFERENCES clinica(id),
    tipo                VARCHAR(50) NOT NULL,           -- Diagrama (REMINDER_48H, REMINDER_24H, REMINDER_2H, SURGERY_72H, POST_CONSULT_SURVEY, REACTIVATION, ANNUAL_PREVENTIVE, HOLIDAY)
    ativo               BOOLEAN NOT NULL DEFAULT TRUE,  -- Diagrama
    offset_minutos      INTEGER,
    threshold_dias      INTEGER,
    template_mensagem   BYTEA NOT NULL,                 -- 🔒 (Diagrama)
    ultima_modificacao_por BIGINT REFERENCES usuario(id),
    deletado_em         TIMESTAMPTZ,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (clinica_id, tipo) WHERE deletado_em IS NULL
);

CREATE TABLE log_automacao (
    id                  BIGSERIAL PRIMARY KEY,
    regra_automacao_id  BIGINT NOT NULL REFERENCES regra_automacao(id),
    paciente_id         BIGINT REFERENCES paciente(id),
    agendamento_id      BIGINT REFERENCES agendamento(id),
    mensagem_renderizada BYTEA,                         -- 🔒
    disparado_em        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status              VARCHAR(20) NOT NULL,           -- ENVIADO | FALHOU | SUPRIMIDO
    motivo_falha        VARCHAR(500)
);

CREATE INDEX idx_log_automacao_regra_tempo ON log_automacao(regra_automacao_id, disparado_em DESC);
CREATE INDEX idx_log_automacao_paciente    ON log_automacao(paciente_id, disparado_em DESC);
```

### 7.5 `pesquisa_satisfacao` (Diagrama: **PesquisaSatisfacao**)

```sql
CREATE TABLE pesquisa_satisfacao (
    id              BIGSERIAL PRIMARY KEY,
    agendamento_id  BIGINT NOT NULL REFERENCES agendamento(id) UNIQUE,
    paciente_id     BIGINT NOT NULL REFERENCES paciente(id),
    medico_id       BIGINT NOT NULL REFERENCES usuario(id),
    nota            SMALLINT,                       -- Diagrama: 0..10, NULL para respostas não-numéricas
    resposta_livre  BYTEA,                          -- 🔒 (comentário aberto / resposta não-numérica)
    data_avaliacao  TIMESTAMPTZ NOT NULL,           -- Diagrama
    requer_revisao  BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (nota IS NULL OR (nota >= 0 AND nota <= 10))
);

CREATE INDEX idx_pesquisa_medico_tempo ON pesquisa_satisfacao(medico_id, data_avaliacao DESC);
```

---

## 8. Compliance & Integração

### 8.1 `log_auditoria` (LGPD)

```sql
CREATE TABLE log_auditoria (
    id              BIGSERIAL PRIMARY KEY,
    clinica_id      BIGINT REFERENCES clinica(id),
    ator_usuario_id BIGINT REFERENCES usuario(id),
    ator_perfil     VARCHAR(30),
    acao            VARCHAR(80) NOT NULL,
    entidade_alvo   VARCHAR(60),
    id_alvo         BIGINT,
    motivo          VARCHAR(500),
    metadata        JSONB,
    ip_address      INET,
    user_agent      VARCHAR(500),
    ocorrido_em     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_log_auditoria_ator      ON log_auditoria(ator_usuario_id, ocorrido_em DESC);
CREATE INDEX idx_log_auditoria_alvo      ON log_auditoria(entidade_alvo, id_alvo, ocorrido_em DESC);
CREATE INDEX idx_log_auditoria_acao      ON log_auditoria(acao, ocorrido_em DESC);
```

### 8.2 `relatorio_incidente`

```sql
CREATE TABLE relatorio_incidente (
    id                  BIGSERIAL PRIMARY KEY,
    clinica_id          BIGINT NOT NULL REFERENCES clinica(id),
    reportado_por       BIGINT REFERENCES usuario(id),
    resumo              VARCHAR(500) NOT NULL,
    titulares_afetados  INTEGER,
    categorias_dados    TEXT[],                         -- {PESSOAL, SENSIVEL_SAUDE}
    detectado_em        TIMESTAMPTZ NOT NULL,
    notificado_anpd_em  TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL DEFAULT 'ABERTO',
    detalhes            JSONB,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_relatorio_status CHECK (status IN ('ABERTO','INVESTIGANDO','NOTIFICADO','FECHADO'))
);
```

### 8.3 `estado_sync_darwin`

```sql
CREATE TABLE estado_sync_darwin (
    id                  BIGSERIAL PRIMARY KEY,
    clinica_id          BIGINT NOT NULL REFERENCES clinica(id),
    recurso             VARCHAR(50) NOT NULL,        -- PACIENTES | AGENDAMENTOS | NOTAS
    ultimo_sync_em      TIMESTAMPTZ,
    ultimo_cursor       VARCHAR(200),
    ultimo_status       VARCHAR(20) NOT NULL DEFAULT 'OCIOSO',
    ultimo_erro         VARCHAR(1000),
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (clinica_id, recurso)
);
```

### 8.4 `whatsapp_payload_log`

```sql
CREATE TABLE whatsapp_payload_log (
    id              BIGSERIAL PRIMARY KEY,
    direcao         VARCHAR(10) NOT NULL,        -- ENTRADA | SAIDA
    payload         JSONB NOT NULL,
    processado_em   TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL DEFAULT 'RECEBIDO',  -- RECEBIDO | PROCESSADO | FALHOU
    motivo_falha    VARCHAR(500),
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 9. Relacionamentos (Mapa Resumido)

```
clinica 1───* usuario 1───* horario_atendente
clinica 1───* paciente 1───* consentimento
clinica 1───* tag *───* paciente_tag *───1 paciente
clinica 1───* janela_horario_ia            (1 linha por dia da semana — Diagrama)
clinica 1───* regra_automacao 1───* log_automacao

usuario  1───0..1 perfil_medico            (quando perfil=MEDICO — Diagrama subclasse)
usuario  1───* permissoes_recepcionista    (quando perfil=RECEPCIONISTA — Diagrama subclasse)
usuario  1───* capacidade_usuario
usuario  1───* mensagem_rapida             (Diagrama "cria e gerencia")
usuario  1───* refresh_token

paciente 1───* atendimento 1───* mensagem 1───* midia_mensagem    (Diagrama "possui" + "contem")
paciente 1───* agendamento *───1 usuario(medico)                  (Diagrama "possui" + "realiza")
paciente 1───* lembrete *───1 usuario(criador)                    (Diagrama "associado a" + "responsavel por")
paciente 1───* pesquisa_satisfacao                                (Diagrama "responde")

atendimento 1───* transferencia_atendimento
agendamento 1───0..1 cancelamento_ia
agendamento 1───0..1 pesquisa_satisfacao
* ──► log_auditoria
clinica 1───* relatorio_incidente
clinica 1───* estado_sync_darwin
```

---

## 10. Soft-delete (R12, BR-02)

Tabelas com `deletado_em`: `paciente`, `usuario`, `tag`, `mensagem_rapida`, `regra_automacao`.

- Hibernate `@SQLRestriction("deletado_em IS NULL")` na entidade.
- Repositories default filtram automaticamente.
- Métodos "incluir deletados" só existem em `PacienteRepository` e `UsuarioRepository` (operações LGPD + auditoria).

**Cascata em soft delete de paciente**:
- `paciente.deletado_em = NOW()`, `status = 'FINALIZADO'`.
- Atendimentos visíveis só para Gestor (audit view).
- Lembretes pendentes → `cancelado = TRUE`.
- Agendamentos futuros → `status = 'CANCELADO'`, `motivo_cancelamento_ia` preenchido se IA.

---

## 11. Índices & Performance

| Caminho crítico | Índice | Target |
|----------------|--------|--------|
| Busca paciente por nome | `idx_paciente_nome_trgm` (GIN trigram) | ≤ 1s (NFR-07) |
| Busca paciente por telefone | `idx_paciente_telefone_norm` | ≤ 1s |
| Lista atendimentos do atendente | `idx_atendimento_atendente_recente` | ≤ 500ms |
| Histórico de mensagens | `idx_mensagem_atendimento_tempo` desc | ≤ 500ms 1ª página |
| Lembretes devidos | `idx_lembrete_devido` (parcial) | ≤ 200ms por tick Quartz |
| Agenda semanal por médico | `idx_agendamento_medico_semana` | ≤ 500ms |
| Agregação de cancelamentos IA | `idx_cancelamento_ia_tempo` | ≤ 1s |
| Auditoria LGPD | `idx_log_auditoria_alvo` | ≤ 2s |

---

## 12. Plano de Migrations Flyway

```
V1__criar_extensoes.sql                       -- pg_trgm
V2__criar_tabelas_lookup.sql                  -- clinica, tipo_agendamento, categoria_mensagem_rapida
V3__criar_tabelas_identidade.sql              -- usuario, perfil_medico, permissoes_recepcionista, capacidade_usuario, horario_atendente, refresh_token
V4__criar_tabelas_paciente.sql                -- paciente, consentimento
V5__criar_tabelas_atendimento.sql             -- atendimento, transferencia_atendimento, mensagem, midia_mensagem
V6__criar_tabelas_agendamento.sql             -- agendamento, janela_horario_ia, cancelamento_ia
V7__criar_tabelas_operacional.sql             -- lembrete, tag, paciente_tag, mensagem_rapida, regra_automacao, log_automacao
V8__criar_tabela_pesquisa_satisfacao.sql
V9__criar_tabelas_compliance.sql              -- log_auditoria, relatorio_incidente
V10__criar_tabelas_integracao.sql             -- estado_sync_darwin, whatsapp_payload_log
V11__criar_fk_circular_paciente_atendimento.sql
V12__seed_lookups.sql                         -- tipo_agendamento, categoria_mensagem_rapida
V13__seed_clinica_e_admin_dev.sql             -- (apenas dev)
```

---

## 13. Máquinas de Estado

### `paciente.status` (BR-01)
```
EM_ATENDIMENTO ──► AGENDADO ──► FINALIZADO
       │              │              │
       └──── follow_up_ativo (boolean ortogonal) ────┘
```

### `atendimento.status` (NOVO — diagrama)
```
ATIVO ──► TRANSFERIDO ──► ATIVO (novo atendente)
ATIVO ──► IA_AUTOMATICO ──► ATIVO (handoff)
ATIVO ──► ENCERRADO
```

### `agendamento.status`
```
AGENDADO ──► CONFIRMADO ──► CONCLUIDO ──► (dispara satisfação)
   │             │              │
   ▼             ▼              ▼
CANCELADO     NO_SHOW       (terminal)
```

---

## 14. Estratégia de Criptografia (R3)

- `AesGcmConverter` implementa `jakarta.persistence.AttributeConverter<String, byte[]>`.
- Formato do ciphertext: `[1-byte versão][12-byte nonce][ciphertext][16-byte GCM tag]`.
- Resolução de chave: `KeyProvider.resolve(versionByte)` lê de env ou KMS.
- Para lookup (ex: busca por CPF), colunas paralelas `<campo>_hash CHAR(64)` com SHA-256 + pepper.
- Pepper é segredo de env por clínica, prevenindo rainbow tables em dumps roubados.

---

## 15. Trace ao Diagrama de Classes

| Elemento do diagrama | Implementação |
|---------------------|----------------|
| `Usuario.login()` | `AuthController.login()` + `JwtService.issueAccessToken()` |
| `Usuario.gerenciarTags()` | `TagController.*` — todos os perfis têm acesso (BR-07) |
| `Paciente.alterarStatus()` | `PacienteService.alterarStatus(id, novoStatus)` — valida BR-01 |
| `Atendimento.transferirAtendente()` | `AtendimentoService.transferirAtendente(id, novoUsuarioId, motivo)` — insere em `transferencia_atendimento` |
| `Lembrete.dispararNotificacao()` | `LembreteScheduler` (Quartz) + `RealtimeBroadcastService` (STOMP) |
| `APIDarwin.importarPacientes()` | `DarwinClient.importarPacientes()` — chamado por `DarwinSyncJob` |
| `APIDarwin.importarAgendamentos()` | `DarwinClient.importarAgendamentos()` |
| `APIDarwin.importarDadosClinicos()` | `DarwinClient.importarDadosClinicos()` — atualiza `paciente.darwin_dados_importados` JSONB |

---

## 16. Conformidade — Checklist Final

- [x] Toda entidade do diagrama tem tabela em pt-BR
- [x] Todo atributo do diagrama está presente
- [x] Todo relacionamento do diagrama tem FK/join
- [x] Hierarquia Usuario → Gestor/Recepcionista/Medico via discriminator + tabelas auxiliares
- [x] `MensagemRapida.atalho` presente
- [x] `Paciente.notasInternas` presente
- [x] `Agendamento.motivoCancelamentoIA` inline + tabela de log
- [x] `JanelaHorarioIA` modelada como N linhas (uma por dia)
- [x] Soft-delete preservado
- [x] Encryption (🔒) preservada
- [x] LGPD audit + consent + incident
- [x] Multi-tenant primitives (clinica_id)
- [x] Métodos do diagrama mapeados para services Spring
