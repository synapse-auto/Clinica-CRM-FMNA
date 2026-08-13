-- Estrutura da nova configuracao: uma ficha consolidada por medico.
-- Esta migration NAO altera, consolida, exclui ou popula linhas existentes.
-- As colunas legadas permanecem temporariamente para preservar todos os dados
-- ate a reorganizacao manual e validada de cada clinica.
ALTER TABLE public.clinica_valores_consulta_medico
  ADD COLUMN atende_ginecologia BOOLEAN NULL,
  ADD COLUMN atende_obstetricia BOOLEAN NULL,
  ADD COLUMN valor_ginecologia NUMERIC NULL,
  ADD COLUMN valor_obstetricia NUMERIC NULL;
