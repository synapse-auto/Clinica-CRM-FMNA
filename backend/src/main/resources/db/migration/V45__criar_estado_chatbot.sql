CREATE TABLE IF NOT EXISTS public.chatbot_estado (
  id BIGSERIAL PRIMARY KEY,
  clinica_id BIGINT NOT NULL,
  atendimento_id TEXT NULL,
  session_id VARCHAR(255) NOT NULL,
  etapa VARCHAR(80) NOT NULL DEFAULT 'menu_principal',
  rota VARCHAR(80) NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'ATIVO',
  dados JSONB NOT NULL DEFAULT '{}'::jsonb,
  tentativas_invalidas INTEGER NOT NULL DEFAULT 0,
  ultima_mensagem_id VARCHAR(255) NULL,
  ultima_resposta JSONB NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT chatbot_estado_clinica_fkey
    FOREIGN KEY (clinica_id)
    REFERENCES public.clinica (id)
    ON DELETE CASCADE,
  CONSTRAINT chatbot_estado_tentativas_check
    CHECK (tentativas_invalidas >= 0),
  CONSTRAINT chatbot_estado_status_check
    CHECK (status IN ('ATIVO', 'CONCLUIDO', 'TRANSFERIDO')),
  CONSTRAINT chatbot_estado_clinica_session_key
    UNIQUE (clinica_id, session_id)
);

CREATE INDEX IF NOT EXISTS idx_chatbot_estado_atendimento
  ON public.chatbot_estado (clinica_id, atendimento_id)
  WHERE atendimento_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chatbot_estado_updated_at
  ON public.chatbot_estado (updated_at);

