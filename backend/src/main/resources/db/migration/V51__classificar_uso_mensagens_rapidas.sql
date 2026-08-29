ALTER TABLE mensagem_rapida
    ADD COLUMN IF NOT EXISTS uso VARCHAR(10) NOT NULL DEFAULT 'AMBOS';

ALTER TABLE mensagem_rapida
    DROP CONSTRAINT IF EXISTS chk_mensagem_rapida_uso;

ALTER TABLE mensagem_rapida
    ADD CONSTRAINT chk_mensagem_rapida_uso
    CHECK (uso IN ('HUMANO', 'CHATBOT', 'AMBOS')) NOT VALID;

ALTER TABLE mensagem_rapida
    VALIDATE CONSTRAINT chk_mensagem_rapida_uso;

CREATE INDEX IF NOT EXISTS idx_mensagem_rapida_clinica_uso
    ON mensagem_rapida(clinica_id, uso)
    WHERE deletado_em IS NULL;
