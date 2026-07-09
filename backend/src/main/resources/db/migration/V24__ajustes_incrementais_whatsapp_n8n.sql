-- Ajustes incrementais de operacionalizacao do WhatsApp / n8n.
-- Mantem idempotencia para refletir alteracoes aplicadas manualmente no banco.

ALTER TABLE agendamento
    ADD COLUMN IF NOT EXISTS crm_atendimento_id TEXT,
    ADD COLUMN IF NOT EXISTS crm_paciente_id TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'agendamento'::regclass
          AND conname = 'agendamento_external_id_key'
    ) THEN
        ALTER TABLE agendamento
            ADD CONSTRAINT agendamento_external_id_key UNIQUE (external_id);
    END IF;
END
$$;

ALTER TABLE paciente
    ADD COLUMN IF NOT EXISTS follow_up_90_dias_enviado_em TIMESTAMPTZ;

-- Contexto do ambiente anterior: created_at esta ligado a filas/configs operacionais
-- e nao ao modelo principal de paciente/agendamento, que neste backend usa criado_em/atualizado_em.
-- Como a fila de follow-up ja possui created_at desde a V8, deixamos a guarda explicita aqui.
ALTER TABLE follow_ups_temporary
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
