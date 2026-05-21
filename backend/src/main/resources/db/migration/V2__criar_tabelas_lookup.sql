CREATE TABLE clinica (
    id              BIGSERIAL PRIMARY KEY,
    nome            VARCHAR(200) NOT NULL,
    razao_social    VARCHAR(200) NOT NULL,
    cnpj            VARCHAR(18)  NOT NULL UNIQUE,
    email_contato   VARCHAR(255) NOT NULL,
    telefone_contato VARCHAR(20) NOT NULL,
    logo_url        VARCHAR(500),
    cor_primaria    VARCHAR(7),
    tema_padrao     VARCHAR(10) DEFAULT 'CLARO',
    fuso_horario    VARCHAR(60) NOT NULL DEFAULT 'America/Sao_Paulo',
    ia_24h          BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

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
