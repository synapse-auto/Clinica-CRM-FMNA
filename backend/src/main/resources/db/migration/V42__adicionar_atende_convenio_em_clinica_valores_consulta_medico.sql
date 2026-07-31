ALTER TABLE IF EXISTS public.clinica_valores_consulta_medico
ADD COLUMN IF NOT EXISTS atende_convenio BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN public.clinica_valores_consulta_medico.atende_convenio
IS 'Indica se o medico atende convenio para o tipo de consulta informado.';
