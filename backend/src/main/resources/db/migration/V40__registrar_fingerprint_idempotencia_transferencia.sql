-- Permite distinguir retry legitimo de reutilizacao conflitante da mesma chave.
-- Nullable para preservar transferencias registradas antes desta versao.
ALTER TABLE transferencia_atendimento
    ADD COLUMN IF NOT EXISTS idempotency_fingerprint VARCHAR(64);
