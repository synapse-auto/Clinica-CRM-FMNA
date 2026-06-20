ALTER TABLE paciente
    ADD COLUMN convenio_status VARCHAR(20),
    ADD COLUMN convenio_revisado_em TIMESTAMPTZ,
    ADD COLUMN convenio_revisado_por BIGINT REFERENCES usuario(id);

ALTER TABLE paciente
    ADD CONSTRAINT chk_paciente_convenio_status
        CHECK (convenio_status IS NULL OR convenio_status IN ('APROVADO', 'RECUSADO', 'PENDENTE'));

ALTER TABLE midia_mensagem
    ALTER COLUMN s3_bucket DROP NOT NULL,
    ALTER COLUMN s3_chave DROP NOT NULL,
    ADD COLUMN nome_arquivo VARCHAR(255);

CREATE TABLE notificacao_atendimento (
    id                  BIGSERIAL PRIMARY KEY,
    usuario_id          BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    atendimento_id      BIGINT NOT NULL REFERENCES atendimento(id) ON DELETE CASCADE,
    mensagem_id         BIGINT REFERENCES mensagem(id) ON DELETE CASCADE,
    tipo                VARCHAR(30) NOT NULL,
    descricao           VARCHAR(255) NOT NULL,
    lida_em             TIMESTAMPTZ,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_notificacao_atendimento_tipo
        CHECK (tipo IN ('NOVA_MENSAGEM', 'ATENDIMENTO_ATRIBUIDO'))
);

CREATE UNIQUE INDEX uk_notificacao_atendimento_mensagem_usuario
    ON notificacao_atendimento(usuario_id, mensagem_id, tipo)
    WHERE mensagem_id IS NOT NULL;

CREATE INDEX idx_notificacao_atendimento_usuario_nao_lida
    ON notificacao_atendimento(usuario_id, criado_em DESC)
    WHERE lida_em IS NULL;
