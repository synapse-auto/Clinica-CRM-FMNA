-- O parser inbound persiste videos recebidos com tipo_media=VIDEO.
-- A constraint recriada pela V36 omitiu esse tipo e fazia a gravacao falhar.
ALTER TABLE mensagem
    DROP CONSTRAINT IF EXISTS chk_mensagem_tipo_media;

ALTER TABLE mensagem
    ADD CONSTRAINT chk_mensagem_tipo_media
    CHECK (tipo_media IN (
        'TEXTO',
        'AUDIO',
        'IMAGEM',
        'VIDEO',
        'DOCUMENTO',
        'TEMPLATE',
        'OUTRO',
        'AI_HANDOFF_ENDED',
        'HUMAN_HANDOFF_START',
        'AI_HANDOFF_SUMMARY'
    )) NOT VALID;

ALTER TABLE mensagem
    VALIDATE CONSTRAINT chk_mensagem_tipo_media;
