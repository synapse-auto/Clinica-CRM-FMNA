CREATE TABLE IF NOT EXISTS paciente_tag (
    paciente_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (paciente_id, tag_id),
    FOREIGN KEY (paciente_id) REFERENCES paciente(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_paciente_tag_paciente
    ON paciente_tag(paciente_id);

CREATE INDEX IF NOT EXISTS idx_paciente_tag_tag
    ON paciente_tag(tag_id);
