-- Distingue transferencias manuais, callbacks N8N e rodizios automaticos.
-- As colunas permanecem nullable para preservar o historico anterior a migration.
ALTER TABLE transferencia_atendimento
    ADD COLUMN IF NOT EXISTS origem VARCHAR(30),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120);

CREATE INDEX IF NOT EXISTS idx_transferencia_atendimento_rodizio
    ON transferencia_atendimento(origem, transferido_em DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_transferencia_atendimento_idempotency_key
    ON transferencia_atendimento(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
