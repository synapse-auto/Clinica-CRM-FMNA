-- A identidade de um agendamento externo pertence a uma clinica e provider.
-- Falha sem alterar dados caso o schema ja contenha identidades compostas duplicadas.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM agendamento
        WHERE external_source IS NOT NULL
        GROUP BY clinica_id, external_source, external_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'V28 bloqueada: existem identidades externas compostas duplicadas em agendamento';
    END IF;
END
$$;

ALTER TABLE agendamento
    DROP CONSTRAINT IF EXISTS agendamento_external_id_key;

CREATE UNIQUE INDEX ux_agendamento_clinica_source_external_id
    ON agendamento (clinica_id, external_source, external_id)
    WHERE external_source IS NOT NULL;
