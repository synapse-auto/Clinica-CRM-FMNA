CREATE TABLE paciente (
    id                          BIGSERIAL PRIMARY KEY,
    clinica_id                  BIGINT NOT NULL REFERENCES clinica(id),
    nome                        BYTEA NOT NULL,
    nome_busca                  VARCHAR(200) NOT NULL,
    cpf                         BYTEA UNIQUE,
    cpf_hash                    CHAR(64) UNIQUE,
    data_nascimento             BYTEA,
    email                       BYTEA,
    email_hash                  CHAR(64),
    telefone                    BYTEA NOT NULL,
    telefone_normalizado        VARCHAR(20) NOT NULL,
    endereco                    BYTEA,
    horario_preferencial        VARCHAR(20),
    notas_internas              BYTEA,
    status                      VARCHAR(20) NOT NULL DEFAULT 'EM_ATENDIMENTO',
    follow_up_ativo             BOOLEAN NOT NULL DEFAULT FALSE,
    atendente_principal_id      BIGINT REFERENCES usuario(id),
    medico_principal_id         BIGINT REFERENCES usuario(id),
    atendimento_atual_id        BIGINT,
    valor_total                 NUMERIC(12,2) DEFAULT 0,
    chave_criptografia_id       VARCHAR(20) NOT NULL DEFAULT 'v1',
    darwin_id_externo           VARCHAR(100) UNIQUE,
    darwin_dados_importados     JSONB,
    google_drive_folder_id      VARCHAR(255) DEFAULT NULL,
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

CREATE TABLE consentimento (
    id                      BIGSERIAL PRIMARY KEY,
    paciente_id             BIGINT NOT NULL REFERENCES paciente(id),
    base_legal              VARCHAR(50) NOT NULL,
    finalidade              VARCHAR(100) NOT NULL,
    versao_texto_consentimento VARCHAR(20) NOT NULL,
    consentimento_dado      BOOLEAN NOT NULL,
    concedido_em            TIMESTAMPTZ NOT NULL,
    concedido_via           VARCHAR(20) NOT NULL,
    revogado_em             TIMESTAMPTZ,
    evidencia_bruta         JSONB,
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_consentimento_paciente_finalidade ON consentimento(paciente_id, finalidade);
