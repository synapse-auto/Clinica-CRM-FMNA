# Variaveis de ambiente

Nao versionar secrets reais. Use valores de exemplo em arquivos de documentacao e configure os valores reais no ambiente do deploy.

## Banco

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/database
SPRING_DATASOURCE_USERNAME=<usuario>
SPRING_DATASOURCE_PASSWORD=<senha>
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_FLYWAY_ENABLED=true
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

## JWT

```text
JWT_SECRET=<segredo-com-32-ou-mais-caracteres>
JWT_EXPIRATION_MS=86400000
```

## Criptografia

```text
CRYPTO_MASTER_KEY=<chave-aes-32-caracteres>
```

## Clinica atual

```text
APP_CLINIC_SLUG=fmna
APP_CLINIC_NAME=FMNA
APP_CLINIC_EXTERNAL_PROVIDER=DARWIN
APP_CLINIC_WHATSAPP_PHONE_NUMBER_ID=<phone-number-id>
```

Valores validos para `APP_CLINIC_EXTERNAL_PROVIDER`:

- `DARWIN`
- `MEDWARE`

## WhatsApp

```text
WHATSAPP_VERIFY_TOKEN=<verify-token-meta>
WHATSAPP_APP_SECRET=<app-secret-meta>
WHATSAPP_ACCESS_TOKEN=<access-token-meta>
WHATSAPP_PHONE_NUMBER_ID=<phone-number-id>
WHATSAPP_GRAPH_API_URL=https://graph.facebook.com/v20.0
```

Cada deploy deve usar suas proprias credenciais de WhatsApp.

## Darwin

```text
DARWIN_ENABLED=true
DARWIN_API_URL=<url-darwin>
DARWIN_API_TOKEN=<token-read-only>
DARWIN_SYNC_CRON=0 0/15 * * * ?
DARWIN_PAGE_SIZE=100
```

Uso esperado: deploy FMNA. Integracao read-only.

## Medware

```text
MEDWARE_API_URL=<url-publica-da-instalacao-medware-ultramedical>/api
MEDWARE_USERNAME=<usuario-api-medware>
MEDWARE_PASSWORD=<senha-ou-hash>
MEDWARE_PASSWORD_IS_HASH=true
MEDWARE_TOKEN_REFRESH_MARGIN_SECONDS=300
MEDWARE_TIMEOUT_SECONDS=30
MEDWARE_DEFAULT_START_DAYS_BACK=30
MEDWARE_DEFAULT_END_DAYS_FORWARD=60
MEDWARE_WEBHOOK_TOKEN=<token-webhook-apenas-etapa-futura>
```

Uso esperado: deploy UltraMedical. A URL real deve ser a URL publica da instalacao Medware da UltraMedical, mantendo o sufixo `/api` quando a publicacao seguir o padrao da documentacao oficial.

O provider Medware atual usa somente leitura: login em `POST /Acesso/login`, `Authorization: Bearer` nas chamadas e endpoints `GET` de pacientes, agendamentos e catalogos auxiliares. Nao configure credenciais reais em arquivos versionados. `MEDWARE_WEBHOOK_TOKEN` fica documentado para etapa futura de webhook, mas nao e usado pelo CRM nesta fase read-only.

## N8N

Configuracao persistida por clinica:

- `usa_n8n`;
- `n8n_webhook_url`.

Nao colocar URL real de webhook em arquivo versionado.

## Seed dev

```text
APP_DEV_SEED_ENABLED=true
APP_DEV_SEED_EMAIL=gestor-fmna@local.test
APP_DEV_SEED_PASSWORD=<senha-local-forte>
```

Use somente em desenvolvimento/homologacao descartavel. Em producao, deixe desabilitado.

## Frontend

```text
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

O frontend deve obter nome, logo, cor e flags da clinica via `/api/configuracoes/clinica-atual`.
