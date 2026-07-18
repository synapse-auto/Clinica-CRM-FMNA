CREATE INDEX IF NOT EXISTS idx_paciente_clinica_nome_busca_ativo
    ON paciente (clinica_id, nome_busca, id)
    WHERE deletado_em IS NULL;

CREATE INDEX IF NOT EXISTS idx_paciente_clinica_external_id_ativo
    ON paciente (clinica_id, external_id)
    WHERE deletado_em IS NULL AND external_id IS NOT NULL;
