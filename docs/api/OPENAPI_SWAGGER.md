# OpenAPI e Swagger UI

O backend usa `springdoc-openapi-starter-webmvc-ui` **2.6.0**, compatível com Spring Boot 3.3.x.
A especificação é dinâmica: controllers, rotas, parâmetros, DTOs e validações são descobertos no
rebuild/restart. Não há `openapi.json` versionado como fonte de verdade.

## Ativação e URLs

Por padrão, `API_DOCS_ENABLED=false`; nesse estado Swagger UI e `/v3/api-docs` não são registrados.
Para ativar em ambiente controlado, configure também credenciais independentes das contas do CRM:

```text
API_DOCS_ENABLED=true
API_DOCS_USERNAME=docs-local
API_DOCS_PASSWORD=<senha-forte-com-pelo-menos-12-caracteres>
```

As URLs são `/swagger-ui.html`, `/v3/api-docs`, `/v3/api-docs.yaml`, `/v3/api-docs/crm` e
`/v3/api-docs/n8n`. As rotas da documentação exigem HTTP Basic; sem credenciais, com credenciais
incorretas ou credenciais ausentes na ativação, o acesso falha com segurança. A senha não é
persistida nem registrada em logs.

Após abrir a documentação com Basic, use **Authorize** e o esquema `bearerAuth` para informar um
JWT do CRM ao testar APIs protegidas. Esse token não é persistido pelo Swagger UI. O Basic da
documentação não autentica APIs do CRM e o JWT não substitui o Basic das páginas de documentação.

## Grupos e exclusões

O grupo `crm` cobre as APIs usuais do CRM, incluindo autenticação, atendimentos, mensagens,
pacientes, equipe, agenda e configurações. O grupo `n8n` cobre apenas `/api/n8n/**`; seus
endpoints usam o contrato `X-N8N-SECRET` e, quando aplicável, `Idempotency-Key`, não o JWT do CRM.

As importações CSV aparecem como `multipart/form-data`: `file`, `mapping` opcional no preview e
`expectedFileHash` mais `mapping` na confirmação. O limite do servidor é 5 MB; são aceitos CSV
UTF-8 (com ou sem BOM) ou Windows-1252, separados por vírgula ou ponto e vírgula. O preview não
persiste e informa amostra, hash e linhas inválidas; a confirmação exige o hash validado.

`/api/webhooks/**` e `/ws/**` são excluídos. Webhooks são contratos de providers externos e não
devem ser disparados manualmente pela interface.

## Segurança e manutenção

Não use credenciais do CRM, JWT, banco, Meta, Uzapi ou N8N para a documentação. Em produção,
habilite-a apenas em ambiente/rede controlados e mantenha `API_DOCS_ENABLED=false` quando não for
necessária.

O formato de erro atual normalmente inclui `timestamp`, `status`, `error`, `message` e `path`; alguns
fluxos também incluem `code` e/ou `details` de validação. As anotações de descrição, exemplos,
roles, segurança, idempotência e exclusões exigem revisão manual quando o contrato de negócio muda.
Senhas de login e troca de senha são marcadas como `writeOnly`/`password`; exemplos não incluem
tokens, secrets ou dados de pacientes.

Erros de validação (400), autenticação (401), autorização (403), recurso ausente (404), conflito
(409) e falha interna (500) usam esse formato conforme o fluxo; o 413 de multipart é emitido pelo
servidor antes de chegar ao controller e pode seguir a resposta padrão do Spring Boot. Por isso não
há um schema único artificial para todos os erros.

Para atualizar a estrutura técnica, recompile e reinicie (ou faça novo deploy) após mudar
controllers, DTOs, rotas ou validações. Revise manualmente as informações de negócio e segurança;
não edite nem versione JSON/YAML estático.
