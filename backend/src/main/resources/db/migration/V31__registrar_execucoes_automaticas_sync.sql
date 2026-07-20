ALTER TABLE integration_sync_log
    ADD COLUMN IF NOT EXISTS origem VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE integration_sync_log
    ADD COLUMN IF NOT EXISTS total_processado INTEGER NOT NULL DEFAULT 0;

ALTER TABLE integration_sync_log
    ADD COLUMN IF NOT EXISTS duracao_ms BIGINT;

ALTER TABLE integration_sync_log
    DROP CONSTRAINT IF EXISTS integration_sync_log_status_check;

ALTER TABLE integration_sync_log
    ADD CONSTRAINT integration_sync_log_status_check
    CHECK (status IN ('EXECUTANDO','SUCESSO','FALHA_PARCIAL','FALHA_TOTAL','IGNORADA_CONCORRENCIA'));

CREATE INDEX IF NOT EXISTS idx_integration_sync_log_clinica_origem_inicio
    ON integration_sync_log (clinica_id, origem, iniciado_em DESC);
