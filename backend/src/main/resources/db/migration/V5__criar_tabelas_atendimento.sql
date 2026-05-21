CREATE TABLE atendimento (
    id                          BIGSERIAL PRIMARY KEY,
    clinica_id                  BIGINT NOT NULL REFERENCES clinica(id),
    paciente_id                 BIGINT NOT NULL REFERENCES paciente(id),
    atendente_principal_id      BIGINT REFERENCES usuario(id),
    data_inicio                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    data_encerramento           TIMESTAMPTZ,
    status                      VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    tratado_por_ia              BOOLEAN NOT NULL DEFAULT FALSE,
    motivo_encerramento         VARCHAR(255),
    ultima_mensagem_em          TIMESTAMPTZ,
    nao_lidas                   INTEGER NOT NULL DEFAULT 0,
    whatsapp_chat_id            VARCHAR(50),
    criado_em                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_atendimento_status CHECK (status IN ('ATIVO','TRANSFERIDO','ENCERRADO','IA_AUTOMATICO'))
);

CREATE INDEX idx_atendimento_paciente              ON atendimento(paciente_id, data_inicio DESC);
CREATE INDEX idx_atendimento_atendente_recente     ON atendimento(atendente_principal_id, ultima_mensagem_em DESC) WHERE status IN ('ATIVO','IA_AUTOMATICO');
CREATE INDEX idx_atendimento_ia                    ON atendimento(tratado_por_ia, ultima_mensagem_em DESC);
CREATE INDEX idx_atendimento_status_ultima_msg     ON atendimento(status, ultima_mensagem_em DESC);

ALTER TABLE paciente ADD CONSTRAINT fk_paciente_atendimento_atual FOREIGN KEY (atendimento_atual_id) REFERENCES atendimento(id) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE transferencia_atendimento (
    id                  BIGSERIAL PRIMARY KEY,
    atendimento_id      BIGINT NOT NULL REFERENCES atendimento(id) ON DELETE CASCADE,
    de_usuario_id       BIGINT REFERENCES usuario(id),
    para_usuario_id     BIGINT NOT NULL REFERENCES usuario(id),
    transferido_por     BIGINT NOT NULL REFERENCES usuario(id),
    motivo              VARCHAR(500),
    transferido_em      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transferencia_atendimento ON transferencia_atendimento(atendimento_id, transferido_em DESC);

CREATE TABLE mensagem (
    id                      BIGSERIAL PRIMARY KEY,
    atendimento_id          BIGINT NOT NULL REFERENCES atendimento(id) ON DELETE CASCADE,
    direcao                 VARCHAR(10) NOT NULL,
    remetente               VARCHAR(20) NOT NULL,
    remetente_usuario_id    BIGINT REFERENCES usuario(id),
    tipo_media              VARCHAR(20) NOT NULL,
    conteudo                BYTEA,
    conteudo_previa         VARCHAR(60),
    data_hora               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    whatsapp_message_id     VARCHAR(100) UNIQUE,
    whatsapp_status         VARCHAR(20),
    mensagem_rapida_id      BIGINT, -- FK será criada quando mensagem_rapida for criada
    entregue_em             TIMESTAMPTZ,
    lida_em                 TIMESTAMPTZ,
    motivo_falha            VARCHAR(255),
    chave_criptografia_id   VARCHAR(20) NOT NULL DEFAULT 'v1',
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_mensagem_remetente CHECK (remetente IN ('PACIENTE','ATENDENTE','IA','SISTEMA')),
    CONSTRAINT chk_mensagem_tipo_media CHECK (tipo_media IN ('TEXTO','AUDIO','IMAGEM','DOCUMENTO','TEMPLATE')),
    CONSTRAINT chk_mensagem_direcao CHECK (direcao IN ('ENTRADA','SAIDA'))
);

CREATE INDEX idx_mensagem_atendimento_tempo ON mensagem(atendimento_id, data_hora DESC);
CREATE INDEX idx_mensagem_nao_lida          ON mensagem(atendimento_id) WHERE lida_em IS NULL AND direcao = 'ENTRADA';

CREATE TABLE midia_mensagem (
    id                  BIGSERIAL PRIMARY KEY,
    mensagem_id         BIGINT NOT NULL REFERENCES mensagem(id) ON DELETE CASCADE,
    tipo_media          VARCHAR(20) NOT NULL,
    mime_type           VARCHAR(120) NOT NULL,
    s3_bucket           VARCHAR(100) NOT NULL,
    s3_chave            BYTEA NOT NULL,
    tamanho_bytes       BIGINT NOT NULL,
    duracao_segundos    INTEGER,
    whatsapp_media_id   VARCHAR(100),
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
