-- Estende a tabela agendamento existente para suportar o espelho local de agendamentos Darwin.
-- A API Darwin não oferece listagem geral de agendamentos por clínica/período (somente por CPF),
-- então agendamentos Darwin são espelhados nesta mesma tabela (já usada pelo Medware), evitando
-- duplicação de entidade. Colunas nullable: nenhum dado existente é afetado.

ALTER TABLE agendamento
    ADD COLUMN profissional_externo_id VARCHAR(100),
    ADD COLUMN profissional_nome VARCHAR(120),
    ADD COLUMN procedimento_externo_id VARCHAR(100),
    ADD COLUMN convenio_externo_id VARCHAR(100),
    ADD COLUMN convenio_nome VARCHAR(120),
    ADD COLUMN local_externo_id VARCHAR(100),
    ADD COLUMN local_nome VARCHAR(120),
    ADD COLUMN timetable_id VARCHAR(100),
    ADD COLUMN sync_status VARCHAR(30) NOT NULL DEFAULT 'SYNCED',
    ADD COLUMN sync_mensagem_erro VARCHAR(255),
    ADD COLUMN sincronizado_em TIMESTAMPTZ;

ALTER TABLE agendamento
    ADD CONSTRAINT chk_agendamento_sync_status
    CHECK (sync_status IN ('SYNCED', 'PENDING_RECONCILIATION', 'FAILED', 'CANCELLED'));

COMMENT ON COLUMN agendamento.sync_status IS
    'Estado de consistência entre a escrita externa (Darwin) e o espelho local. SYNCED = ambos ok; '
    'PENDING_RECONCILIATION = escrita externa confirmada mas persistência local falhou (requer reconciliação, '
    'nunca reexecutar a escrita externa às cegas); FAILED = escrita externa falhou (nada foi persistido como '
    'sucesso); CANCELLED = cancelado/excluído.';
COMMENT ON COLUMN agendamento.sync_mensagem_erro IS
    'Mensagem sanitizada da última falha de sincronização. Nunca contém CPF, telefone, token ou corpo completo.';
