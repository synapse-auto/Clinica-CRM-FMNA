# Homologacao e deploy seguro multi-clinica

## Estrategia

O CRM usa o mesmo codigo para dois deploys separados:

- FMNA / Clinica Femina: banco proprio, WhatsApp proprio e provider externo `DARWIN`;
- UltraMedical: banco proprio, WhatsApp proprio e provider externo `MEDWARE`.

Nao ha SaaS multi-tenant com dados misturados no mesmo banco nesta fase. Ainda assim, as tabelas principais mantem `clinica_id` para organizacao, seguranca e evolucao futura.

Dashboard, agenda e pacientes leem somente o modelo interno do CRM. Darwin e Medware sao fontes externas read-only e alimentam dados normalizados via `ExternalSyncService`.

## Checklist comum

1. Criar banco separado para o deploy.
2. Configurar variaveis de ambiente do deploy.
3. Rodar a aplicacao com Flyway habilitado.
4. Validar `flyway_schema_history`.
5. Validar a clinica atual via `/api/configuracoes/clinica-atual`.
6. Validar `/api/dashboard` com banco vazio e com dados internos.
7. Validar webhook WhatsApp com `metadata.phone_number_id` do deploy.
8. Validar N8N apenas se `usaN8n=true` e `n8nWebhookUrl` estiver configurada.

## FMNA

Variaveis essenciais:

```text
APP_CLINIC_SLUG=fmna
APP_CLINIC_NAME=FMNA
APP_CLINIC_EXTERNAL_PROVIDER=DARWIN
APP_CLINIC_WHATSAPP_PHONE_NUMBER_ID=<phone-number-id-fmna>
WHATSAPP_PHONE_NUMBER_ID=<phone-number-id-fmna>
DARWIN_API_URL=<url-darwin>
DARWIN_API_TOKEN=<token-read-only-darwin>
```

Validacoes:

- `/api/configuracoes/clinica-atual` retorna `slug=fmna`;
- `externalProvider` da clinica no banco e `DARWIN`;
- sync usa `ExternalSyncService` e `DarwinProvider`;
- dashboard nao chama Darwin diretamente;
- `usa_cirurgias_na_agenda=true`, se a agenda FMNA precisar exibir cirurgias.

## UltraMedical

Variaveis essenciais:

```text
APP_CLINIC_SLUG=ultramedical
APP_CLINIC_NAME=UltraMedical
APP_CLINIC_EXTERNAL_PROVIDER=MEDWARE
APP_CLINIC_WHATSAPP_PHONE_NUMBER_ID=<phone-number-id-ultra>
WHATSAPP_PHONE_NUMBER_ID=<phone-number-id-ultra>
MEDWARE_API_URL=<url-publica-medware-ultramedical>/api
MEDWARE_USERNAME=<usuario-api-medware>
MEDWARE_PASSWORD=<senha-ou-hash-fora-do-git>
MEDWARE_PASSWORD_IS_HASH=true
MEDWARE_TOKEN_REFRESH_MARGIN_SECONDS=300
MEDWARE_TIMEOUT_SECONDS=30
MEDWARE_DEFAULT_START_DAYS_BACK=30
MEDWARE_DEFAULT_END_DAYS_FORWARD=60
```

Validacoes:

- `/api/configuracoes/clinica-atual` retorna `slug=ultramedical`;
- `externalProvider` da clinica no banco e `MEDWARE`;
- `MedwareProvider` esta como skeleton ate existir documentacao oficial da API;
- dashboard nao chama Medware diretamente;
- `usa_cirurgias_na_agenda=false` para nao exibir cirurgias na experiencia de ultrassonografia.

## Reset de banco em ambiente sem producao

Use apenas em ambiente de desenvolvimento ou homologacao descartavel:

```powershell
docker compose --profile test down -v
docker compose --profile test up -d postgres-test
```

Nunca use reset destrutivo em producao.

## Validar Flyway

```sql
SELECT installed_rank, version, script, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Esperado: V1 a V7 com `success=true`.

## Testar Dashboard

1. Autentique com usuario gestor do ambiente.
2. Chame:

```http
GET /api/dashboard?periodo=DIA&data=2026-06-15
```

Resultado esperado:

- resposta 200;
- zeros/listas vazias em banco vazio;
- dados agregados a partir de `paciente`, `mensagem`, `atendimento` e `agendamento`;
- nenhuma chamada a Darwin ou Medware.

## Testar WhatsApp inbound

O payload da Meta precisa conter:

```json
{
  "metadata": {
    "phone_number_id": "phone-number-id-do-deploy"
  }
}
```

Resultado esperado:

- phone number conhecido resolve uma clinica;
- phone number desconhecido e ignorado com log sem PII;
- paciente criado com `external_source=WHATSAPP`;
- atendimento e mensagem ficam ligados ao `clinica_id` correto.

## Testar N8N

N8N so emite quando a clinica esta com:

- `usa_n8n=true`;
- `n8n_webhook_url` preenchida.

Eventos previstos:

- `novo_lead`;
- `nova_mensagem`;
- `mudanca_status`;
- `agendamento_criado`;
- `agendamento_cancelado`;
- `follow_up`;
- `pesquisa_satisfacao`.

O payload publico nao deve conter tokens nem URL interna do webhook.
