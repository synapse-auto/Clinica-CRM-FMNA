-- O fluxo de transferencia N8N cria notificacoes internas de handoff.
-- Preserva os tipos existentes e inclui o novo tipo operacional.
ALTER TABLE notificacao_atendimento
    DROP CONSTRAINT IF EXISTS chk_notificacao_atendimento_tipo;

ALTER TABLE notificacao_atendimento
    ADD CONSTRAINT chk_notificacao_atendimento_tipo
    CHECK (tipo IN (
        'NOVA_MENSAGEM',
        'ATENDIMENTO_ATRIBUIDO',
        'TRANSFERENCIA_IA'
    ));
