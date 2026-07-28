-- Os eventos internos de handoff ja sao persistidos pelo AtendimentoService.
-- A constraint original de V5 aceitava apenas mensagens WhatsApp convencionais.
ALTER TABLE mensagem
    DROP CONSTRAINT IF EXISTS chk_mensagem_direcao;

ALTER TABLE mensagem
    ADD CONSTRAINT chk_mensagem_direcao
    CHECK (direcao IN ('ENTRADA', 'SAIDA', 'SISTEMA'));

ALTER TABLE mensagem
    DROP CONSTRAINT IF EXISTS chk_mensagem_tipo_media;

ALTER TABLE mensagem
    ADD CONSTRAINT chk_mensagem_tipo_media
    CHECK (tipo_media IN (
        'TEXTO',
        'AUDIO',
        'IMAGEM',
        'DOCUMENTO',
        'TEMPLATE',
        'OUTRO',
        'AI_HANDOFF_ENDED',
        'HUMAN_HANDOFF_START',
        'AI_HANDOFF_SUMMARY'
    ));
