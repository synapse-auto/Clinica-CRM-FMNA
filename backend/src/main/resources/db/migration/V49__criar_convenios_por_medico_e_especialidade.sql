-- Matriz de convenios por medico e especialidade.
-- Esta migration cria somente estrutura: nao insere, altera ou remove dados.

CREATE TABLE IF NOT EXISTS public.convenio_medico_especialidade (
  id BIGSERIAL PRIMARY KEY,
  clinica_id BIGINT NOT NULL REFERENCES public.clinica(id),
  medico_id INTEGER NOT NULL
    REFERENCES public.clinica_valores_consulta_medico(id),
  convenio_id BIGINT NOT NULL
    REFERENCES public.convenios_clinica(id),
  atende_ginecologia BOOLEAN NOT NULL DEFAULT false,
  atende_obstetricia BOOLEAN NOT NULL DEFAULT false,
  ativo BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

  CONSTRAINT uk_convenio_medico_especialidade
    UNIQUE (clinica_id, medico_id, convenio_id),

  CONSTRAINT ck_convenio_medico_alguma_especialidade
    CHECK (atende_ginecologia OR atende_obstetricia)
);

CREATE INDEX IF NOT EXISTS idx_convenio_medico_especialidade_busca
  ON public.convenio_medico_especialidade (
    clinica_id,
    medico_id,
    ativo
  );

CREATE INDEX IF NOT EXISTS idx_convenio_medico_especialidade_convenio
  ON public.convenio_medico_especialidade (
    convenio_id
  );
