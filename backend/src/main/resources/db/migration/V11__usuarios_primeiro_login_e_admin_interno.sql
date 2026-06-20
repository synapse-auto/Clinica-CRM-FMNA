ALTER TABLE usuario
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN admin_interno BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_usuario_clinica_visivel
    ON usuario(clinica_id, ativo)
    WHERE deletado_em IS NULL AND admin_interno = FALSE;
