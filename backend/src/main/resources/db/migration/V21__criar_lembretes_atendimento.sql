CREATE TABLE IF NOT EXISTS atendimento_lembrete (
    id BIGSERIAL PRIMARY KEY,
    clinica_id BIGINT NOT NULL REFERENCES clinica(id),
    atendimento_id BIGINT NOT NULL REFERENCES atendimento(id),
    paciente_id BIGINT REFERENCES paciente(id),
    mensagem VARCHAR(500) NOT NULL,
    lembrar_em TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    criado_por BIGINT REFERENCES usuario(id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_atendimento_lembrete_status
        CHECK (status IN ('PENDENTE', 'CONCLUIDO', 'CANCELADO'))
);

CREATE INDEX IF NOT EXISTS idx_atendimento_lembrete_clinica_atendimento
    ON atendimento_lembrete (clinica_id, atendimento_id);

CREATE INDEX IF NOT EXISTS idx_atendimento_lembrete_clinica_status_lembrar
    ON atendimento_lembrete (clinica_id, status, lembrar_em);

CREATE INDEX IF NOT EXISTS idx_atendimento_lembrete_atendimento_status
    ON atendimento_lembrete (atendimento_id, status);
