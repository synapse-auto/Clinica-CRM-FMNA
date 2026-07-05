ALTER TABLE integration_sync_log
    ADD COLUMN IF NOT EXISTS data_inicio DATE;

ALTER TABLE integration_sync_log
    ADD COLUMN IF NOT EXISTS data_fim DATE;

ALTER TABLE atendimento
    ADD COLUMN IF NOT EXISTS humano_desde TIMESTAMPTZ;

UPDATE atendimento
SET humano_desde = COALESCE(ultima_mensagem_em, atualizado_em, data_inicio, NOW())
WHERE tratado_por_ia = false
  AND humano_desde IS NULL
  AND status = 'ATIVO';

CREATE INDEX IF NOT EXISTS idx_atendimento_humano_desde
    ON atendimento (humano_desde)
    WHERE tratado_por_ia = false
      AND status = 'ATIVO';
