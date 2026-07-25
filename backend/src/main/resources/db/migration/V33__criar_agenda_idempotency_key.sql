-- Registro durável de idempotência para escritas da Agenda expostas ao N8N
-- (criação de agendamento/encaixe). Substitui qualquer dedup por conteúdo da
-- requisição: a chave de idempotência é fornecida pelo workflow N8N e a
-- restrição única abaixo garante que retries concorrentes nunca dupliquem um
-- agendamento, mesmo após restart da aplicação (estado em banco, não em memória).

CREATE TABLE agenda_idempotency_key (
    id BIGSERIAL PRIMARY KEY,
    clinica_id BIGINT NOT NULL REFERENCES clinica (id),
    operacao VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    agendamento_local_id BIGINT REFERENCES agendamento (id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_agenda_idempotency_key UNIQUE (clinica_id, operacao, idempotency_key)
);

CREATE INDEX idx_agenda_idempotency_key_lookup
    ON agenda_idempotency_key (clinica_id, operacao, idempotency_key);

COMMENT ON TABLE agenda_idempotency_key IS
    'Isolamento por clinica_id+operacao+idempotency_key. request_hash (SHA-256 do payload '
    'normalizado) detecta reuso de chave com corpo diferente (409). Nunca contém CPF, nome, '
    'telefone ou corpo bruto da requisição.';
