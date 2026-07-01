CREATE TABLE IF NOT EXISTS atendimento_tag (
    atendimento_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (atendimento_id, tag_id),
    FOREIGN KEY (atendimento_id) REFERENCES atendimento(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_atendimento_tag_atendimento
    ON atendimento_tag(atendimento_id);

CREATE INDEX IF NOT EXISTS idx_atendimento_tag_tag
    ON atendimento_tag(tag_id);
