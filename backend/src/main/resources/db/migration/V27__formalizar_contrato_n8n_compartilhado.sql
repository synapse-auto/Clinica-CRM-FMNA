-- Formaliza o contrato SQL compartilhado pelos workflows N8N das clinicas.
-- A tabela e criada apenas em deployments que ainda nao possuem o historico.
CREATE TABLE IF NOT EXISTS n8n_chat_histories (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    message JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE follow_ups_temporary
    ADD COLUMN IF NOT EXISTS crm_paciente_id TEXT;

ALTER TABLE follow_ups_temporary
    ADD COLUMN IF NOT EXISTS crm_atendimento_id TEXT;

-- FMNA legou BIGINT nesta coluna; TEXT preserva ids numericos e futuros ids
-- textuais sem conversao destrutiva. Deployments ja padronizados nao mudam.
DO $$
DECLARE
    tipo_chatid TEXT;
BEGIN
    SELECT c.udt_name
      INTO tipo_chatid
      FROM information_schema.columns c
     WHERE c.table_schema = 'public'
       AND c.table_name = 'paciente'
       AND c.column_name = 'chatid';

    IF tipo_chatid IS NOT NULL AND tipo_chatid <> 'text' THEN
        ALTER TABLE paciente
            ALTER COLUMN chatid TYPE TEXT
            USING chatid::TEXT;
    END IF;
END $$;

-- Compatibilidade temporaria: o workflow atual omite external_source e nao
-- existe backfill confiavel para inventar um valor nesses agendamentos.
ALTER TABLE agendamento
    ALTER COLUMN external_source DROP NOT NULL;
