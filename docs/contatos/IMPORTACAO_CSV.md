# Importação de contatos por CSV

## Escopo

A Agenda de Contatos importa somente `nome` e `telefone`. Cada contato efetivamente
criado recebe a origem interna `IMPORTACAO`, exibida como **Importação**.

## Formato e limites

São aceitos arquivos `.csv` de até 5 MB, com até 50.000 linhas de dados e 100 colunas.
O parser aceita vírgula ou ponto e vírgula, UTF-8 (com ou sem BOM), Windows-1252,
CRLF/LF e valores entre aspas. Colunas sem cabeçalho são aceitas somente quando estão
inteiramente vazias (por exemplo, uma vírgula final gerada por uma planilha); se houver
qualquer valor nessa coluna, o arquivo é rejeitado. XLS/XLSX, ODS, PDF, compactados e JSON
não são aceitos.

Modelo:

```csv
nome;telefone
Maria da Silva;5583999999999
João Souza;83988887777
```

Os cabeçalhos de nome e telefone são sugeridos automaticamente e podem ser corrigidos
no modal. A mesma coluna não pode ser usada para ambos os campos.

## Segurança e isolamento

Os endpoints exigem Gestor ou Recepcionista ativo. A clínica é resolvida no backend pelo
contexto autenticado/configurado; o cliente não envia `clinicId`. O arquivo não é
persistido e não é registrado em logs. Logs operacionais contêm apenas IDs, hash reduzido,
contagens e duração.

O preview calcula SHA-256 sem gravar dados. Na confirmação, o arquivo é enviado novamente
e o hash é conferido antes de uma nova validação. A confirmação usa lock da clínica e
grava os novos contatos em lotes.

## Telefones e duplicidade

O telefone é normalizado por `WhatsappPhoneIdentityService` e
`WhatsappPhoneNormalizer`, incluindo aliases brasileiros estruturalmente seguros. A busca
de duplicidade é limitada à clínica atual. Um contato existente não é alterado; a primeira
linha válida do arquivo prevalece e as linhas equivalentes seguintes são marcadas como
duplicadas. Reimportar o mesmo arquivo não cria contatos adicionais.

## O que a importação não faz

Ela não cria atendimento, mensagem, `whatsappChatId`, tag, notificação, lembrete ou
agendamento. Também não chama Meta, Uzapi, N8N, Medware ou Darwin e não cria IDs externos.

## API

- `POST /api/pacientes/importacoes/csv/preview`: multipart com `file` e, opcionalmente,
  `mapping`; devolve hash, cabeçalhos, amostra e validação sem persistir.
- `POST /api/pacientes/importacoes/csv`: multipart com `file`, `expectedFileHash` e
  `mapping`; confirma a importação.

O relatório de erros não inclui valores de nome ou telefone. O download usa UTF-8 com BOM,
ponto e vírgula, escape CSV e proteção contra fórmulas.
