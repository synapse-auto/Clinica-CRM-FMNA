CREATE INDEX IF NOT EXISTS idx_atendimento_clinica_ultima_msg
    ON atendimento (clinica_id, ultima_mensagem_em DESC);

CREATE INDEX IF NOT EXISTS idx_atendimento_clinica_status_ultima_msg
    ON atendimento (clinica_id, status, ultima_mensagem_em DESC);

CREATE INDEX IF NOT EXISTS idx_mensagem_data_atendimento
    ON mensagem (data_hora, atendimento_id);

CREATE INDEX IF NOT EXISTS idx_paciente_clinica_telefone_norm
    ON paciente (clinica_id, telefone_normalizado)
    WHERE deletado_em IS NULL;

CREATE INDEX IF NOT EXISTS idx_agendamento_clinica_status_inicio
    ON agendamento (clinica_id, status, data_hora_inicio);

CREATE INDEX IF NOT EXISTS idx_integration_sync_log_clinica_provider_status_inicio
    ON integration_sync_log (clinica_id, external_provider, status, iniciado_em DESC);
