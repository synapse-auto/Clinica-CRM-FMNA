CREATE TABLE agendamento (
    id                      BIGSERIAL PRIMARY KEY,
    clinica_id              BIGINT NOT NULL REFERENCES clinica(id),
    paciente_id             BIGINT NOT NULL REFERENCES paciente(id),
    medico_id               BIGINT REFERENCES usuario(id),
    external_source         VARCHAR(20) NOT NULL,
    external_id             VARCHAR(100) NOT NULL,
    data_hora_inicio        TIMESTAMPTZ NOT NULL,
    data_hora_fim           TIMESTAMPTZ,
    tipo                    VARCHAR(40),
    servico_nome            VARCHAR(120),
    status                  VARCHAR(30) NOT NULL DEFAULT 'AGENDADO',
    origem                  VARCHAR(40) NOT NULL DEFAULT 'INTEGRACAO_EXTERNA',
    confirmado_em           TIMESTAMPTZ,
    cancelado_em            TIMESTAMPTZ,
    motivo_cancelamento     VARCHAR(255),
    external_payload        JSONB,
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_agendamento_external_source CHECK (external_source IN ('DARWIN','MEDWARE','WHATSAPP','MANUAL'))
);

CREATE UNIQUE INDEX uk_agendamento_clinica_external
    ON agendamento(clinica_id, external_source, external_id);

CREATE INDEX idx_agendamento_clinica_inicio
    ON agendamento(clinica_id, data_hora_inicio);

CREATE INDEX idx_agendamento_paciente_inicio
    ON agendamento(paciente_id, data_hora_inicio DESC);

CREATE TABLE integration_sync_log (
    id                          BIGSERIAL PRIMARY KEY,
    clinica_id                  BIGINT NOT NULL REFERENCES clinica(id),
    external_provider           VARCHAR(20) NOT NULL,
    iniciado_em                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    concluido_em                TIMESTAMPTZ,
    status                      VARCHAR(20) NOT NULL DEFAULT 'EXECUTANDO'
                                    CHECK (status IN ('EXECUTANDO','SUCESSO','FALHA_PARCIAL','FALHA_TOTAL')),
    pacientes_processados       INTEGER NOT NULL DEFAULT 0,
    pacientes_criados           INTEGER NOT NULL DEFAULT 0,
    pacientes_atualizados       INTEGER NOT NULL DEFAULT 0,
    agendamentos_processados    INTEGER NOT NULL DEFAULT 0,
    agendamentos_criados        INTEGER NOT NULL DEFAULT 0,
    agendamentos_atualizados    INTEGER NOT NULL DEFAULT 0,
    agendamentos_ignorados      INTEGER NOT NULL DEFAULT 0,
    updated_after_utilizado     TIMESTAMPTZ,
    mensagem_erro               TEXT,
    CONSTRAINT chk_integration_sync_provider CHECK (external_provider IN ('DARWIN','MEDWARE'))
);

CREATE INDEX idx_integration_sync_log_clinica_provider
    ON integration_sync_log(clinica_id, external_provider, iniciado_em DESC);
