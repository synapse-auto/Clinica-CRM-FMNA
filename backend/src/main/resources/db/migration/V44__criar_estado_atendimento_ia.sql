CREATE TABLE IF NOT EXISTS public.atendimento_estado_ia (
  id BIGSERIAL PRIMARY KEY,
  clinica_id BIGINT NOT NULL DEFAULT 1,
  atendimento_id TEXT NULL,
  session_id VARCHAR(255) NULL,
  etapa VARCHAR(80) NOT NULL DEFAULT 'coleta_pessoal',
  retomar_etapa VARCHAR(80) NULL,
  intencao VARCHAR(80) NULL,
  dados JSONB NOT NULL DEFAULT '{}'::jsonb,
  aguardando_confirmacao_resumo BOOLEAN NOT NULL DEFAULT false,
  resumo_apresentado JSONB NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT fk_atendimento_estado_ia_clinica
    FOREIGN KEY (clinica_id)
    REFERENCES public.clinica (id)
    ON DELETE CASCADE,
  CONSTRAINT ck_atendimento_estado_ia_chave
    CHECK (atendimento_id IS NOT NULL OR session_id IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_atendimento_estado_ia_clinica_atendimento
ON public.atendimento_estado_ia (clinica_id, atendimento_id)
WHERE atendimento_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_atendimento_estado_ia_clinica_session
ON public.atendimento_estado_ia (clinica_id, session_id)
WHERE atendimento_id IS NULL AND session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_atendimento_estado_ia_atualizado
ON public.atendimento_estado_ia (clinica_id, updated_at DESC);
