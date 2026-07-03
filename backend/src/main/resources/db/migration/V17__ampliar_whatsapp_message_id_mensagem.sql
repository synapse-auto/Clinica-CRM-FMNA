ALTER TABLE mensagem
    ALTER COLUMN whatsapp_message_id TYPE VARCHAR(255);

COMMENT ON COLUMN mensagem.whatsapp_message_id IS 'ID Meta WhatsApp (wamid) usado para idempotencia.';
