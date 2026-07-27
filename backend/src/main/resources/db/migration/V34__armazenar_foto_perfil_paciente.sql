CREATE TABLE IF NOT EXISTS paciente_foto_perfil (
    paciente_id             BIGINT PRIMARY KEY REFERENCES paciente(id) ON DELETE CASCADE,
    clinica_id              BIGINT NOT NULL REFERENCES clinica(id) ON DELETE CASCADE,
    provider                VARCHAR(20) NOT NULL,
    conteudo                BYTEA,
    content_type            VARCHAR(30),
    sha256                  VARCHAR(64),
    tamanho_bytes           BIGINT,
    status                  VARCHAR(30) NOT NULL DEFAULT 'NO_PHOTO',
    tentativas              INTEGER NOT NULL DEFAULT 0,
    ultima_tentativa_em     TIMESTAMPTZ,
    proxima_tentativa_em    TIMESTAMPTZ,
    obtida_em               TIMESTAMPTZ,
    motivo_ultima_falha     VARCHAR(100),
    atualizada_em           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_paciente_foto_provider
        CHECK (provider IN ('META', 'UAZAP')),
    CONSTRAINT chk_paciente_foto_status
        CHECK (status IN ('PENDING', 'SUCCESS', 'NO_PHOTO', 'TEMPORARY_FAILURE', 'PERMANENT_FAILURE')),
    CONSTRAINT chk_paciente_foto_tamanho
        CHECK (tamanho_bytes IS NULL OR tamanho_bytes BETWEEN 0 AND 2097152),
    CONSTRAINT chk_paciente_foto_conteudo
        CHECK (
            (conteudo IS NULL AND content_type IS NULL AND sha256 IS NULL AND tamanho_bytes IS NULL)
            OR
            (conteudo IS NOT NULL AND content_type IS NOT NULL AND sha256 IS NOT NULL AND tamanho_bytes IS NOT NULL)
        )
);

CREATE INDEX IF NOT EXISTS idx_paciente_foto_clinica_retry
    ON paciente_foto_perfil(clinica_id, status, proxima_tentativa_em);
