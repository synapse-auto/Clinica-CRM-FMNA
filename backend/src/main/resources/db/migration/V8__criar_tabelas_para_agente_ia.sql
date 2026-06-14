-- Tabela de valores/preços dos serviços
CREATE TABLE IF NOT EXISTS clinica_valores (
    id          SERIAL PRIMARY KEY,
    servico     VARCHAR(255) NOT NULL,
    valor       NUMERIC(10,2) NOT NULL,
    observacao  TEXT,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Tabela de médicos
CREATE TABLE IF NOT EXISTS clinica_medicos (
    id              SERIAL PRIMARY KEY,
    medico          VARCHAR(255) NOT NULL,
    especialidade   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Tabela de dados gerais da clínica (chave-valor)
CREATE TABLE IF NOT EXISTS clinica_dados (
    id          SERIAL PRIMARY KEY,
    nome        VARCHAR(255) NOT NULL UNIQUE,
    informacao  TEXT NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);