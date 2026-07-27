CREATE TABLE IF NOT EXISTS cancelamento_agendamento (
    id BIGSERIAL PRIMARY KEY,
    clinica_id BIGINT NOT NULL REFERENCES clinica(id),
    paciente_id BIGINT NOT NULL REFERENCES paciente(id),
    agendamento_id BIGINT REFERENCES agendamento(id),
    atendimento_id BIGINT REFERENCES atendimento(id),
    motivo TEXT NOT NULL,
    origem VARCHAR(40) NOT NULL,
    external_provider VARCHAR(20),
    external_agendamento_id VARCHAR(120),
    status_cancelamento VARCHAR(30) NOT NULL,
    status_sincronizacao VARCHAR(30) NOT NULL,
    mensagem_erro_sincronizacao VARCHAR(255),
    idempotency_key VARCHAR(120),
    coletado_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cancelamento_agendamento_clinica_idempotency
        UNIQUE (clinica_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_cancelamento_agendamento_clinica_coletado
    ON cancelamento_agendamento(clinica_id, coletado_em DESC);
CREATE INDEX IF NOT EXISTS idx_cancelamento_agendamento_clinica_paciente
    ON cancelamento_agendamento(clinica_id, paciente_id);
CREATE INDEX IF NOT EXISTS idx_cancelamento_agendamento_clinica_agendamento
    ON cancelamento_agendamento(clinica_id, agendamento_id);
CREATE INDEX IF NOT EXISTS idx_cancelamento_agendamento_clinica_origem
    ON cancelamento_agendamento(clinica_id, origem);
CREATE INDEX IF NOT EXISTS idx_cancelamento_agendamento_clinica_sync
    ON cancelamento_agendamento(clinica_id, status_sincronizacao);
