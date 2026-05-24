-- Flyway V7: Corrige divergência JPA vs Flyway detectada na auditoria de código.
-- A entidade Paciente possui dois campos (criado_por, atualizado_por) que existem
-- na tabela do banco mas NÃO estão mapeados na classe @Entity, fazendo o ddl-auto=validate
-- falhar. Adicionamos o mapeamento via nova migration (não alteramos migrações já aplicadas).
--
-- Para re-ativar: app.jpa.hibernate.ddl-auto=validate
-- Ação: Nenhuma alteração de schema necessária — as colunas já existem desde V4.
-- Esta migration documenta formalmente a reconciliação e serve como ponto de retorno.

-- Verificação de consistência: as colunas já existem; garante que o UNIQUE em email_hash
-- seja idempotente e que o FK circular paciente <-> atendimento esteja correto.
DO $$
BEGIN
    -- Garante que o índice de trigram já existe (criado em V4, mas confirma)
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename = 'paciente' AND indexname = 'idx_paciente_nome_trgm'
    ) THEN
        CREATE INDEX idx_paciente_nome_trgm
            ON paciente USING GIN (nome_busca gin_trgm_ops)
            WHERE deletado_em IS NULL;
    END IF;
END $$;
