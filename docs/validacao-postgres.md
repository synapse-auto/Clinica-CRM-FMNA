# Validacao PostgreSQL das migrations multi-clinica

Este roteiro valida as migrations em PostgreSQL real, com Flyway e `ddl-auto=validate`.
Nao use H2 para essa verificacao: o schema usa `pg_trgm`, `jsonb`, indices parciais e tipos do PostgreSQL.

## Banco de teste

Suba um PostgreSQL limpo:

```powershell
docker compose --profile test up -d postgres-test
```

Dados do servico:

- host local: `localhost`
- porta local: `55432`
- banco: `clinicafemina_test`
- usuario: `postgres`
- senha: `postgres_test_pass`

O `postgres-test` usa `tmpfs` em `/var/lib/postgresql/data`. Ele nao usa o volume principal do projeto. Para parar e descartar dados temporarios:

```powershell
docker compose --profile test down -v
```

Para abrir o `psql`:

```powershell
docker compose --profile test exec postgres-test psql -U postgres -d clinicafemina_test
```

## Validacao automatizada por script

Execute a partir da raiz do repositorio:

```powershell
.\scripts\validate-postgres-fmna.ps1
.\scripts\validate-postgres-ultra.ps1
```

Os scripts usam apenas valores locais de teste, limpam as variaveis ao final e nao usam secrets reais.

Para limpar o banco de teste manualmente:

```powershell
.\scripts\cleanup-postgres-test.ps1
```

## Validacao manual FMNA/Darwin

Execute a partir de `backend/` depois de subir o `postgres-test`:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:55432/clinicafemina_test"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="postgres_test_pass"
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME="org.postgresql.Driver"
$env:SPRING_FLYWAY_ENABLED="true"
$env:SPRING_JPA_HIBERNATE_DDL_AUTO="validate"

$env:APP_DEV_SEED_ENABLED="true"
$env:APP_DEV_SEED_EMAIL="gestor-fmna@local.test"
$env:APP_DEV_SEED_PASSWORD="senha-local-forte-fmna"
$env:APP_CLINIC_SLUG="fmna"
$env:APP_CLINIC_NAME="FMNA"
$env:APP_CLINIC_EXTERNAL_PROVIDER="DARWIN"
$env:APP_CLINIC_WHATSAPP_PHONE_NUMBER_ID="phone-fmna"

.\gradlew.bat test --tests "com.synapse.clinicafemina.BackendApplicationTests" --rerun-tasks
```

Resultado esperado:

- build com `BUILD SUCCESSFUL`;
- Flyway executa V1 a V8;
- Hibernate valida o schema sem erro;
- `clinica` contem `slug=fmna`, `external_provider=DARWIN` e `whatsapp_phone_number_id=phone-fmna`.

## Validacao manual UltraMedical/Medware

Limpe e suba novamente o banco:

```powershell
docker compose --profile test down -v
docker compose --profile test up -d postgres-test
```

Execute a partir de `backend/`:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:55432/clinicafemina_test"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="postgres_test_pass"
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME="org.postgresql.Driver"
$env:SPRING_FLYWAY_ENABLED="true"
$env:SPRING_JPA_HIBERNATE_DDL_AUTO="validate"

$env:APP_DEV_SEED_ENABLED="true"
$env:APP_DEV_SEED_EMAIL="gestor-ultra@local.test"
$env:APP_DEV_SEED_PASSWORD="senha-local-forte-ultra"
$env:APP_CLINIC_SLUG="ultramedical"
$env:APP_CLINIC_NAME="UltraMedical"
$env:APP_CLINIC_EXTERNAL_PROVIDER="MEDWARE"
$env:APP_CLINIC_WHATSAPP_PHONE_NUMBER_ID="phone-ultra"

.\gradlew.bat test --tests "com.synapse.clinicafemina.BackendApplicationTests" --rerun-tasks
```

Resultado esperado:

- build com `BUILD SUCCESSFUL`;
- `clinica.slug=ultramedical`;
- `tipo_clinica=ULTRASSONOGRAFIA`;
- `external_provider=MEDWARE`;
- `usa_cirurgias_na_agenda=false`;
- `whatsapp_phone_number_id=phone-ultra`.
- `follow_up_config`, `consulta_lembrete_config`, `mensagem_festiva_config` e `follow_ups_temporary` existem com `clinica_id`.

## Consultas SQL de validacao

```sql
SELECT installed_rank, version, script, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT slug, nome, tipo_clinica, external_provider, usa_cirurgias_na_agenda, whatsapp_phone_number_id
FROM clinica
ORDER BY id;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('paciente', 'agendamento', 'integration_sync_log')
ORDER BY table_name;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('follow_up_config', 'consulta_lembrete_config', 'mensagem_festiva_config', 'follow_ups_temporary')
ORDER BY table_name;

SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'follow_ups_temporary'
  AND column_name IN ('clinica_id', 'paciente_id', 'scheduled_at', 'status', 'payload')
ORDER BY column_name;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('clinica_valores', 'clinica_medicos', 'clinica_dados')
ORDER BY table_name;

SELECT column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'paciente'
  AND column_name IN ('cpf_hash', 'email_hash', 'external_source', 'external_id', 'external_payload', 'google_drive_folder_id')
ORDER BY column_name;

SELECT column_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'paciente'
  AND column_name IN ('darwin_id_externo', 'darwin_dados_importados');

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
    'uk_clinica_slug',
    'uk_clinica_whatsapp_phone_number_id',
    'uk_paciente_clinica_external',
    'uk_agendamento_clinica_external',
    'idx_integration_sync_log_clinica_provider',
    'idx_clinica_valores_clinica',
    'idx_clinica_medicos_clinica',
    'idx_clinica_dados_clinica',
    'idx_follow_up_config_clinica',
    'idx_consulta_lembrete_config_clinica',
    'idx_mensagem_festiva_config_clinica',
    'idx_follow_ups_temporary_clinica_paciente',
    'idx_follow_ups_temporary_clinica_status',
    'idx_follow_ups_temporary_clinica_scheduled_at',
    'idx_follow_ups_temporary_clinica_status_scheduled_at'
  )
ORDER BY indexname;

SELECT tc.table_name, tc.constraint_name
FROM information_schema.table_constraints tc
WHERE tc.table_schema = 'public'
  AND tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_name IN ('clinica_valores', 'clinica_medicos', 'clinica_dados', 'follow_up_config', 'consulta_lembrete_config', 'mensagem_festiva_config', 'follow_ups_temporary')
ORDER BY tc.table_name, tc.constraint_name;

SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'mensagem'::regclass
  AND conname = 'chk_mensagem_remetente';

SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'agendamento'
  AND column_name = 'requer_revisao';
```

Resultados esperados:

- `flyway_schema_history` mostra V1 a V8 com `success=true`.
- `paciente.cpf_hash` e `paciente.email_hash` sao `character varying(64)`.
- `paciente.external_source`, `external_id`, `external_payload` e `google_drive_folder_id` existem.
- A consulta de campos Darwin legados nao retorna linhas.
- `paciente`, `agendamento`, `integration_sync_log`, `clinica_valores`, `clinica_medicos`, `clinica_dados`, `follow_up_config`, `consulta_lembrete_config`, `mensagem_festiva_config` e `follow_ups_temporary` existem.
- As tabelas de IA tem `clinica_id` e FK para `clinica`.
- `follow_ups_temporary` tem `clinica_id`, `paciente_id`, `scheduled_at`, FK para `paciente(id)` e indices por clinica/status/horario.
- `chk_mensagem_remetente` aceita `PACIENTE`, `ATENDENTE`, `IA` e `SISTEMA`.
- No schema atual, `agendamento.requer_revisao` nao existe. Se a coluna for adicionada antes do commit, ela deve aparecer como `boolean`, `is_nullable=NO` e default `false`.

## Troubleshooting

### Docker nao inicia

Verifique se o Docker Desktop esta aberto e se o comando abaixo responde:

```powershell
docker version
```

### WSL

Em Windows, confirme que o Docker Desktop esta com integracao WSL habilitada e que o compose usa o contexto correto:

```powershell
docker context ls
```

### Connection refused

Confira se o container esta saudavel:

```powershell
docker compose --profile test ps postgres-test
```

Se a porta `55432` estiver ocupada, ajuste `TEST_DB_PORT` antes de subir:

```powershell
$env:TEST_DB_PORT="55433"
docker compose --profile test up -d postgres-test
```

### Password authentication failed

Descarte o banco temporario e suba novamente:

```powershell
docker compose --profile test down -v
docker compose --profile test up -d postgres-test
```

### Schema-validation: wrong column type

Compare a coluna no PostgreSQL com a anotacao JPA:

```sql
SELECT column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'paciente'
ORDER BY column_name;
```

Como o projeto ainda nao esta em producao, corrija a migration base consolidada correspondente e rode o reset do `postgres-test`.

### Missing column

Confirme se a migration esperada entrou em `flyway_schema_history` e se o nome fisico da coluna coincide com `@Column(name = "...")`.

```sql
SELECT version, script, success
FROM flyway_schema_history
ORDER BY installed_rank;
```
