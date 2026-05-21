CREATE TABLE usuario (
    id              BIGSERIAL PRIMARY KEY,
    clinica_id      BIGINT NOT NULL REFERENCES clinica(id),
    nome            VARCHAR(200) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    telefone        VARCHAR(20),
    senha_hash      VARCHAR(72) NOT NULL,
    perfil          VARCHAR(20) NOT NULL,
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

CREATE TABLE perfil_medico (
    usuario_id      BIGINT PRIMARY KEY REFERENCES usuario(id) ON DELETE CASCADE,
    especialidade   VARCHAR(50) NOT NULL,
    crm             VARCHAR(20) NOT NULL,
    bio             TEXT,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_perfil_medico_especialidade CHECK (
        especialidade IN ('OBSTETRICIA_PRE_NATAL','GINECOLOGIA','ULTRASSONOGRAFIA','CLINICA_GERAL')
    )
);

CREATE TABLE permissoes_recepcionista (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    aba_permitida   VARCHAR(40) NOT NULL,
    UNIQUE (usuario_id, aba_permitida)
);

CREATE TABLE capacidade_usuario (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    capacidade      VARCHAR(50) NOT NULL,
    concedida_por   BIGINT REFERENCES usuario(id),
    concedida_em    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (usuario_id, capacidade)
);

CREATE TABLE horario_atendente (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    dia_semana      SMALLINT NOT NULL CHECK (dia_semana BETWEEN 0 AND 6),
    hora_inicio     TIME NOT NULL,
    hora_fim        TIME NOT NULL,
    ativo           BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (hora_inicio < hora_fim)
);

CREATE INDEX idx_horario_atendente_usuario ON horario_atendente(usuario_id, dia_semana);

CREATE TABLE refresh_token (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL UNIQUE,
    emitido_em      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expira_em       TIMESTAMPTZ NOT NULL,
    revogado_em     TIMESTAMPTZ,
    user_agent      VARCHAR(500),
    ip_address      INET
);

CREATE INDEX idx_refresh_token_usuario ON refresh_token(usuario_id) WHERE revogado_em IS NULL;
