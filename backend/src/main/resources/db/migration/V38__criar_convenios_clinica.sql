CREATE TABLE IF NOT EXISTS convenios_clinica (
    id BIGSERIAL PRIMARY KEY,
    convenio VARCHAR(150) NOT NULL,
    status VARCHAR(30) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_convenios_clinica_convenio UNIQUE (convenio)
);

CREATE INDEX IF NOT EXISTS idx_convenios_clinica_status
    ON convenios_clinica(status);
