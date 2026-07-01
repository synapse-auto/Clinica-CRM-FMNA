CREATE TABLE tag (
    id BIGSERIAL PRIMARY KEY,
    clinica_id BIGINT NOT NULL REFERENCES clinica(id) ON DELETE CASCADE,
    nome VARCHAR(80) NOT NULL,
    cor VARCHAR(7) NOT NULL DEFAULT '#0d9488',
    descricao TEXT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deletado_em TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_tag_clinica_nome_ativo
    ON tag(clinica_id, lower(nome))
    WHERE deletado_em IS NULL;

CREATE INDEX idx_tag_clinica_ativo
    ON tag(clinica_id, ativo)
    WHERE deletado_em IS NULL;

INSERT INTO categoria_mensagem_rapida (codigo, rotulo, cor) VALUES
    ('ABERTURA', 'Abertura', '#0ea5e9'),
    ('AGENDAMENTO', 'Agendamento', '#0d9488'),
    ('ORIENTACOES', 'Orientações', '#7c3aed'),
    ('FINANCEIRO', 'Financeiro', '#f97316'),
    ('ENCERRAMENTO', 'Encerramento', '#db2777')
ON CONFLICT (codigo) DO NOTHING;

CREATE TABLE mensagem_rapida (
    id BIGSERIAL PRIMARY KEY,
    clinica_id BIGINT NOT NULL REFERENCES clinica(id) ON DELETE CASCADE,
    categoria_id SMALLINT REFERENCES categoria_mensagem_rapida(id),
    titulo VARCHAR(120) NOT NULL,
    atalho VARCHAR(40) NOT NULL,
    conteudo TEXT NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deletado_em TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_mensagem_rapida_clinica_atalho_ativo
    ON mensagem_rapida(clinica_id, lower(atalho))
    WHERE deletado_em IS NULL;

CREATE INDEX idx_mensagem_rapida_clinica_ativo
    ON mensagem_rapida(clinica_id, ativo)
    WHERE deletado_em IS NULL;

ALTER TABLE horario_atendente
    ADD COLUMN IF NOT EXISTS deletado_em TIMESTAMPTZ;

CREATE INDEX idx_horario_atendente_usuario_ativo
    ON horario_atendente(usuario_id, ativo)
    WHERE deletado_em IS NULL;
