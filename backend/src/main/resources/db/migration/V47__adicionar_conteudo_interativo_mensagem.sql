-- Estrutura normalizada e cifrada pela aplicacao para reconstruir mensagens interativas no historico.
-- Registros anteriores permanecem nulos e continuam sendo exibidos como mensagens comuns.
ALTER TABLE mensagem
    ADD COLUMN conteudo_interativo BYTEA;
