CREATE TABLE follow_up_config (
    id BIGSERIAL PRIMARY KEY,
    clinica_id BIGINT NOT NULL REFERENCES clinica(id) ON DELETE CASCADE,
    nome VARCHAR(120) NOT NULL,
    descricao TEXT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    gatilho VARCHAR(80) NOT NULL,
    canal VARCHAR(40) NOT NULL DEFAULT 'WHATSAPP',
    delay_quantidade INTEGER,
    delay_unidade VARCHAR(20),
    horario_envio TIME,
    mensagem_template TEXT,
    config_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_follow_up_config_clinica_nome UNIQUE (clinica_id, nome),
    CONSTRAINT chk_follow_up_config_delay_quantidade CHECK (delay_quantidade IS NULL OR delay_quantidade >= 0),
    CONSTRAINT chk_follow_up_config_delay_unidade CHECK (
        delay_unidade IS NULL OR delay_unidade IN ('MINUTOS', 'HORAS', 'DIAS', 'SEMANAS', 'MESES')
    ),
    CONSTRAINT chk_follow_up_config_canal CHECK (canal IN ('WHATSAPP', 'EMAIL', 'SMS', 'TELEFONE', 'INTERNO'))
);

CREATE INDEX idx_follow_up_config_clinica ON follow_up_config(clinica_id);
CREATE INDEX idx_follow_up_config_clinica_ativo ON follow_up_config(clinica_id, ativo);

CREATE TABLE consulta_lembrete_config (
    id BIGSERIAL PRIMARY KEY,
    clinica_id BIGINT NOT NULL REFERENCES clinica(id) ON DELETE CASCADE,
    nome VARCHAR(120) NOT NULL,
    descricao TEXT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    canal VARCHAR(40) NOT NULL DEFAULT 'WHATSAPP',
    antecedencia_quantidade INTEGER NOT NULL,
    antecedencia_unidade VARCHAR(20) NOT NULL,
    horario_envio TIME,
    permite_confirmacao BOOLEAN NOT NULL DEFAULT TRUE,
    permite_cancelamento BOOLEAN NOT NULL DEFAULT TRUE,
    permite_reagendamento BOOLEAN NOT NULL DEFAULT TRUE,
    mensagem_template TEXT,
    config_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_consulta_lembrete_config_clinica_nome UNIQUE (clinica_id, nome),
    CONSTRAINT chk_consulta_lembrete_antecedencia_quantidade CHECK (antecedencia_quantidade >= 0),
    CONSTRAINT chk_consulta_lembrete_antecedencia_unidade CHECK (
        antecedencia_unidade IN ('MINUTOS', 'HORAS', 'DIAS', 'SEMANAS')
    ),
    CONSTRAINT chk_consulta_lembrete_canal CHECK (canal IN ('WHATSAPP', 'EMAIL', 'SMS', 'TELEFONE', 'INTERNO'))
);

CREATE INDEX idx_consulta_lembrete_config_clinica ON consulta_lembrete_config(clinica_id);
CREATE INDEX idx_consulta_lembrete_config_clinica_ativo ON consulta_lembrete_config(clinica_id, ativo);

CREATE TABLE mensagem_festiva_config (
    id BIGSERIAL PRIMARY KEY,
    clinica_id BIGINT NOT NULL REFERENCES clinica(id) ON DELETE CASCADE,
    chave VARCHAR(80) NOT NULL,
    nome VARCHAR(120) NOT NULL,
    mes_dia CHAR(5) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    canal VARCHAR(40) NOT NULL DEFAULT 'WHATSAPP',
    mensagem_template TEXT NOT NULL,
    config_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_mensagem_festiva_config_clinica_chave UNIQUE (clinica_id, chave),
    CONSTRAINT chk_mensagem_festiva_mes_dia CHECK (mes_dia ~ '^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$'),
    CONSTRAINT chk_mensagem_festiva_canal CHECK (canal IN ('WHATSAPP', 'EMAIL', 'SMS', 'TELEFONE', 'INTERNO'))
);

CREATE INDEX idx_mensagem_festiva_config_clinica ON mensagem_festiva_config(clinica_id);
CREATE INDEX idx_mensagem_festiva_config_clinica_ativo ON mensagem_festiva_config(clinica_id, ativo);
CREATE INDEX idx_mensagem_festiva_config_clinica_mes_dia ON mensagem_festiva_config(clinica_id, mes_dia);

CREATE TABLE follow_ups_temporary (
    id BIGSERIAL PRIMARY KEY,
    clinica_id BIGINT NOT NULL REFERENCES clinica(id) ON DELETE CASCADE,
    paciente_id BIGINT NOT NULL REFERENCES paciente(id) ON DELETE CASCADE,
    follow_up_config_id BIGINT REFERENCES follow_up_config(id) ON DELETE SET NULL,
    titulo VARCHAR(160) NOT NULL,
    descricao TEXT,
    origem VARCHAR(60) NOT NULL DEFAULT 'MANUAL',
    status VARCHAR(40) NOT NULL DEFAULT 'PENDENTE',
    scheduled_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    canceled_at TIMESTAMPTZ,
    cancel_reason TEXT,
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_follow_ups_temporary_origem CHECK (origem IN ('MANUAL', 'FOLLOW_UP_CONFIG', 'CONSULTA_LEMBRETE', 'MENSAGEM_FESTIVA', 'N8N', 'SISTEMA')),
    CONSTRAINT chk_follow_ups_temporary_status CHECK (status IN ('PENDENTE', 'PROCESSANDO', 'PROCESSADO', 'EXECUTADO', 'CANCELADO', 'FALHOU'))
);

CREATE INDEX idx_follow_ups_temporary_clinica_paciente ON follow_ups_temporary(clinica_id, paciente_id);
CREATE INDEX idx_follow_ups_temporary_clinica_status ON follow_ups_temporary(clinica_id, status);
CREATE INDEX idx_follow_ups_temporary_clinica_scheduled_at ON follow_ups_temporary(clinica_id, scheduled_at);
CREATE INDEX idx_follow_ups_temporary_clinica_status_scheduled_at ON follow_ups_temporary(clinica_id, status, scheduled_at);
