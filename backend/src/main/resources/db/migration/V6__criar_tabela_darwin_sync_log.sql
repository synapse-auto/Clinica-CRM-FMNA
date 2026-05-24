-- Flyway V6: Tabela de log de sincronização com a API Darwin
-- Rastreia cada execução do job com métricas e controle de updated_after incremental.

CREATE TABLE darwin_sync_log (
    id                          BIGSERIAL PRIMARY KEY,
    iniciado_em                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    concluido_em                TIMESTAMPTZ,
    status                      VARCHAR(20)  NOT NULL DEFAULT 'EXECUTANDO'
                                    CHECK (status IN ('EXECUTANDO','SUCESSO','FALHA_PARCIAL','FALHA_TOTAL')),
    pacientes_processados       INTEGER NOT NULL DEFAULT 0,
    pacientes_criados           INTEGER NOT NULL DEFAULT 0,
    pacientes_atualizados       INTEGER NOT NULL DEFAULT 0,
    agendamentos_processados    INTEGER NOT NULL DEFAULT 0,
    updated_after_utilizado     TIMESTAMPTZ,
    mensagem_erro               TEXT
);

-- Índices para pesquisa de status e ordenação cronológica
CREATE INDEX idx_darwin_sync_log_status ON darwin_sync_log(status);
CREATE INDEX idx_darwin_sync_log_iniciado ON darwin_sync_log(iniciado_em DESC);
