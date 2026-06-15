CREATE TABLE clinica (
    id              BIGSERIAL PRIMARY KEY,
    nome            VARCHAR(200) NOT NULL,
    slug            VARCHAR(80) NOT NULL,
    tipo_clinica    VARCHAR(30) NOT NULL DEFAULT 'PRE_NATAL',
    external_provider VARCHAR(20) NOT NULL DEFAULT 'DARWIN',
    razao_social    VARCHAR(200) NOT NULL,
    cnpj            VARCHAR(18)  NOT NULL UNIQUE,
    email_contato   VARCHAR(255) NOT NULL,
    telefone_contato VARCHAR(20) NOT NULL,
    logo_url        VARCHAR(500),
    cor_primaria    VARCHAR(7),
    tema_padrao     VARCHAR(10) DEFAULT 'CLARO',
    fuso_horario    VARCHAR(60) NOT NULL DEFAULT 'America/Sao_Paulo',
    ia_24h          BOOLEAN NOT NULL DEFAULT FALSE,
    usa_cirurgias_na_agenda BOOLEAN NOT NULL DEFAULT TRUE,
    follow_up_automatico BOOLEAN NOT NULL DEFAULT FALSE,
    usa_n8n BOOLEAN NOT NULL DEFAULT FALSE,
    n8n_webhook_url VARCHAR(500),
    whatsapp_phone_number_id VARCHAR(80),
    whatsapp_business_account_id VARCHAR(80),
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_clinica_slug UNIQUE (slug),
    CONSTRAINT chk_clinica_tipo_clinica CHECK (tipo_clinica IN ('PRE_NATAL','ULTRASSONOGRAFIA')),
    CONSTRAINT chk_clinica_external_provider CHECK (external_provider IN ('DARWIN','MEDWARE'))
);

CREATE UNIQUE INDEX uk_clinica_whatsapp_phone_number_id
    ON clinica(whatsapp_phone_number_id)
    WHERE whatsapp_phone_number_id IS NOT NULL;

CREATE TABLE clinica_valores (
    id          BIGSERIAL PRIMARY KEY,
    clinica_id  BIGINT NOT NULL REFERENCES clinica(id) ON DELETE CASCADE,
    servico     VARCHAR(255) NOT NULL,
    valor       NUMERIC(10,2) NOT NULL,
    observacao  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_clinica_valores_valor_nao_negativo CHECK (valor >= 0),
    CONSTRAINT uk_clinica_valores_servico UNIQUE (clinica_id, servico)
);

CREATE INDEX idx_clinica_valores_clinica ON clinica_valores(clinica_id);

CREATE TABLE clinica_medicos (
    id              BIGSERIAL PRIMARY KEY,
    clinica_id      BIGINT NOT NULL REFERENCES clinica(id) ON DELETE CASCADE,
    medico          VARCHAR(255) NOT NULL,
    especialidade   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_clinica_medicos_nome_especialidade UNIQUE (clinica_id, medico, especialidade)
);

CREATE INDEX idx_clinica_medicos_clinica ON clinica_medicos(clinica_id);

CREATE TABLE clinica_dados (
    id          BIGSERIAL PRIMARY KEY,
    clinica_id  BIGINT NOT NULL REFERENCES clinica(id) ON DELETE CASCADE,
    nome        VARCHAR(255) NOT NULL,
    informacao  TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_clinica_dados_nome UNIQUE (clinica_id, nome)
);

CREATE INDEX idx_clinica_dados_clinica ON clinica_dados(clinica_id);

CREATE TABLE tipo_agendamento (
    id      SMALLSERIAL PRIMARY KEY,
    codigo  VARCHAR(30) NOT NULL UNIQUE,
    rotulo  VARCHAR(80) NOT NULL
);

CREATE TABLE categoria_mensagem_rapida (
    id      SMALLSERIAL PRIMARY KEY,
    codigo  VARCHAR(30) NOT NULL UNIQUE,
    rotulo  VARCHAR(80) NOT NULL,
    cor     VARCHAR(7)
);
