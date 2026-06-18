ALTER TABLE agendamento
  ADD COLUMN IF NOT EXISTS confirmacao_enviada integer;

ALTER TABLE consulta_lembrete_config
  ADD COLUMN IF NOT EXISTS prazo_de_exclusao_sem_confirmacao_quantidade integer,
  ADD COLUMN IF NOT EXISTS prazo_de_exclusao_sem_confirmacao_unidade varchar(10);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'consulta_lembrete_config'::regclass
      AND conname  = 'chk_prazo_exclusao_unidade'
  ) THEN
    ALTER TABLE consulta_lembrete_config
      ADD CONSTRAINT chk_prazo_exclusao_unidade
      CHECK (
        prazo_de_exclusao_sem_confirmacao_unidade IS NULL
        OR prazo_de_exclusao_sem_confirmacao_unidade
           IN ('MINUTOS','HORAS','DIAS','SEMANAS')
      );
  END IF;
END
$$;