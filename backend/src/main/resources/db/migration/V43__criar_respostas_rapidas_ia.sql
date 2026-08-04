CREATE TABLE IF NOT EXISTS public.respostas_rapidas_ia (
  id BIGSERIAL PRIMARY KEY,
  clinica_id BIGINT NOT NULL DEFAULT 1,
  chave VARCHAR(80) NOT NULL,
  mensagem TEXT NOT NULL,
  ativo BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT fk_respostas_rapidas_ia_clinica
    FOREIGN KEY (clinica_id)
    REFERENCES public.clinica (id)
    ON DELETE CASCADE,
  CONSTRAINT uk_respostas_rapidas_ia_clinica_chave
    UNIQUE (clinica_id, chave)
);

CREATE INDEX IF NOT EXISTS idx_respostas_rapidas_ia_clinica_ativo
ON public.respostas_rapidas_ia (clinica_id, ativo);
