-- As três respostas rápidas abaixo são templates exclusivos da automação da IA.
-- O atalho é o identificador funcional estável; não dependemos de título ou conteúdo.
UPDATE mensagem_rapida
SET uso = 'CHATBOT'
WHERE lower(atalho) IN (
    '/chatbot_cancelamento_automatico',
    '/chatbot_lembrete_confirmacao_presenca',
    '/chatbot_reativacao_clientes_inativos'
)
  AND uso <> 'CHATBOT';
