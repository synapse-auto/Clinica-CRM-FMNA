-- Uma linha por medico. Os valores permanecem administrativos; o chatbot usa
-- somente os booleanos de atendimento e o professional_id do Darwin.
ALTER TABLE public.clinica_valores_consulta_medico
  ADD COLUMN professional_id UUID NULL,
  ADD COLUMN atende_ginecologia BOOLEAN NULL,
  ADD COLUMN atende_obstetricia BOOLEAN NULL,
  ADD COLUMN valor_ginecologia NUMERIC NULL,
  ADD COLUMN valor_obstetricia NUMERIC NULL;

WITH consolidado AS (
  SELECT
    clinica_id,
    medico,
    MIN(id) AS id_destino,
    COALESCE(BOOL_OR(valor_consulta > 0) FILTER (
      WHERE lower(tipo_consulta) LIKE '%gineco%'
    ), false) AS atende_ginecologia,
    COALESCE(BOOL_OR(valor_consulta > 0) FILTER (
      WHERE lower(tipo_consulta) LIKE '%obstetr%'
         OR lower(tipo_consulta) LIKE '%pre%natal%'
    ), false) AS atende_obstetricia,
    COALESCE(MAX(valor_consulta) FILTER (
      WHERE lower(tipo_consulta) LIKE '%gineco%'
    ), 0) AS valor_ginecologia,
    COALESCE(MAX(valor_consulta) FILTER (
      WHERE lower(tipo_consulta) LIKE '%obstetr%'
         OR lower(tipo_consulta) LIKE '%pre%natal%'
    ), 0) AS valor_obstetricia,
    COALESCE(BOOL_OR(atende_convenio), false) AS atende_convenio
  FROM public.clinica_valores_consulta_medico
  GROUP BY clinica_id, medico
)
UPDATE public.clinica_valores_consulta_medico destino
SET
  atende_ginecologia = consolidado.atende_ginecologia,
  atende_obstetricia = consolidado.atende_obstetricia,
  valor_ginecologia = consolidado.valor_ginecologia,
  valor_obstetricia = consolidado.valor_obstetricia,
  atende_convenio = consolidado.atende_convenio,
  updated_at = now()
FROM consolidado
WHERE destino.id = consolidado.id_destino;

DELETE FROM public.clinica_valores_consulta_medico origem
USING (
  SELECT clinica_id, medico, MIN(id) AS id_destino
  FROM public.clinica_valores_consulta_medico
  GROUP BY clinica_id, medico
) consolidado
WHERE origem.clinica_id = consolidado.clinica_id
  AND origem.medico = consolidado.medico
  AND origem.id <> consolidado.id_destino;

ALTER TABLE public.clinica_valores_consulta_medico
  DROP CONSTRAINT uk_clinica_valores_consulta_medico;

ALTER TABLE public.clinica_valores_consulta_medico
  DROP COLUMN tipo_consulta,
  DROP COLUMN valor_consulta;

ALTER TABLE public.clinica_valores_consulta_medico
  ALTER COLUMN atende_ginecologia SET NOT NULL,
  ALTER COLUMN atende_ginecologia SET DEFAULT false,
  ALTER COLUMN atende_obstetricia SET NOT NULL,
  ALTER COLUMN atende_obstetricia SET DEFAULT false,
  ALTER COLUMN valor_ginecologia SET NOT NULL,
  ALTER COLUMN valor_ginecologia SET DEFAULT 0,
  ALTER COLUMN valor_obstetricia SET NOT NULL,
  ALTER COLUMN valor_obstetricia SET DEFAULT 0;

ALTER TABLE public.clinica_valores_consulta_medico
  ADD CONSTRAINT uk_clinica_valores_consulta_medico
  UNIQUE (clinica_id, medico);

CREATE INDEX idx_clinica_valores_consulta_medico_professional
  ON public.clinica_valores_consulta_medico (clinica_id, professional_id);
