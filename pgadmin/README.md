# GUIs de gestão visual do Postgres

Duas interfaces gráficas pra administrar o banco da Clínica Femina (criar/dropar tabelas
e colunas, editar dados, rodar SQL) — sem precisar de cliente SQL externo. Use a que
preferir; ambas batem no mesmo banco.

| Ferramenta | URL | Quando usar |
|------------|-----|-------------|
| **pgAdmin 4** | http://localhost:5050 | Completa: diagramas ER, query tool, conexão pré-cadastrada. |
| **Adminer**   | http://localhost:8081 | Leve e rápida pra editar dados / SQL pontual. |

## Como subir

```bash
docker compose up -d pgadmin adminer
```

---

# pgAdmin 4

## Acesso

- URL: http://localhost:5050
- Login (definido por env vars, com defaults locais):
  - **Email**: `PGADMIN_EMAIL` (default `admin@clinicafemina.local`)
  - **Senha**: `PGADMIN_PASSWORD` (default `admin_pgadmin_local`)

A conexão com a DB remota (`clinica_auto` em `2.24.97.199:5432`) já vem pré-cadastrada
via [servers.json](./servers.json). Ao expandir o servidor, o pgAdmin pede a **senha do
Postgres** (a mesma de `SPRING_DATASOURCE_PASSWORD`) — digite uma vez e marque "Save".

## Configurar credenciais (recomendado)

Defina no `.env` da raiz (ou no `backend/.env`):

```env
PGADMIN_EMAIL=seu-email@dominio.com
PGADMIN_PASSWORD=uma_senha_forte
```

---

# Adminer

- URL: http://localhost:8081
- Tela de login (host já pré-preenchido com `ADMINER_DEFAULT_SERVER`):
  - **Sistema**: PostgreSQL
  - **Servidor**: `2.24.97.199` (ou o valor de `ADMINER_DEFAULT_SERVER`)
  - **Usuário**: `postgres`
  - **Senha**: a mesma de `SPRING_DATASOURCE_PASSWORD`
  - **Base de dados**: `clinica_auto`

Adminer é stateless (sem volume) — nada a persistir.

---

## ⚠️ Workflow seguro — NÃO quebrar o app

O schema é gerenciado por **Flyway** (`backend/src/main/resources/db/migration/`) e
mapeado por **JPA entities** (`ddl-auto: none`). Mexer no schema só pelo pgAdmin causa
**schema drift**: a mudança some num restore, não vai pra produção e pode quebrar o
boot do Hibernate.

Regras:

1. **Editar dados (linhas/valores)** pelo pgAdmin → OK, livre.
2. **Mudar schema** (add/drop coluna, tabela, índice, constraint):
   - Faça o teste exploratório no pgAdmin se quiser, MAS
   - Consolide a mudança numa **nova migration Flyway** `V8__...sql` (nunca edite
     migrations já aplicadas), e
   - Atualize a **entity JPA** correspondente em `backend/.../entity/`.
   - Só assim a mudança é versionada, reproduzível e segura pra deploy.
3. **Nunca dropar** tabelas/colunas que tenham entity ou migration ativa sem antes
   remover o uso no código.

## Segurança

- pgAdmin fica exposto em `:5050`. Em produção, coloque atrás de rede interna/VPN ou
  reverse proxy com auth — não exponha publicamente.
- Credenciais do Postgres não ficam salvas em `servers.json` (só host/porta/usuário).
