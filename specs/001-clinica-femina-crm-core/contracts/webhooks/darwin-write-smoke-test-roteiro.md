# Roteiro de Smoke Test — Escrita Real na Darwin (pós-deploy)

**Não executado nesta sessão.** Este roteiro existe porque os testes automatizados
de `DarwinClient`/`DarwinAgendaProvider` usam mocks — provam o contrato (paths,
métodos, headers, mapeamento de erros), **não** que a API Darwin real aceita essas
chamadas. Nenhuma escrita real deve ser considerada validada até este roteiro ser
executado manualmente, uma vez, contra o ambiente de produção (ou um ambiente
Darwin de homologação equivalente) por alguém com autorização para criar dados de
teste na Darwin.

## Pré-requisitos

- `DARWIN_API_URL` e `DARWIN_API_TOKEN` configurados e válidos no ambiente alvo.
- Um paciente de teste **explicitamente aprovado** pela clínica para uso em teste
  (CPF de teste reconhecido pela equipe Darwin, ou paciente fictício criado só
  para este roteiro) — **nunca usar dados de um paciente real** para este teste.
- Acesso ao painel Darwin (ou à equipe que o opera) para confirmar visualmente que
  os registros criados neste roteiro aparecem e depois são revertidos/arquivados.
- Executar em horário de baixo uso, fora do expediente da clínica, para minimizar
  risco de colisão com agendamentos reais.

## Passo a passo

1. **Consulta de sanidade (read-only, sem risco)**
   `GET /api/integracoes/darwin/status` → confirma que a integração está
   `DISPONIVEL` antes de prosseguir. Se não estiver, **parar aqui** e não
   prosseguir com os passos de escrita.

2. **Criar paciente de teste**
   Via `POST /api/n8n/agenda/pacientes` (ou diretamente o endpoint Darwin, se
   testado fora do CRM) com o CPF de teste aprovado. Confirmar:
   - Resposta `201` com `id` e `nome` retornados.
   - O paciente aparece no painel Darwin com os dados esperados.

3. **Consultar horários disponíveis**
   `GET /api/n8n/agenda/horarios?date=<data-futura>` para obter um `timetableId`
   real e livre. Confirmar que a resposta reflete a grade real da clínica no
   painel Darwin.

4. **Criar agendamento de teste**
   `POST /api/n8n/agenda` com o paciente do passo 2, o `timetableId` do passo 3,
   e uma `Idempotency-Key` única para este teste (ex.:
   `smoke-test-<data>-<hora>`). Confirmar:
   - Resposta `201` com `syncStatus` `SYNCED` ou `PENDING_RECONCILIATION`.
   - Se `PENDING_RECONCILIATION`, aguardar alguns segundos e consultar
     `GET /api/agenda/{idLocal}` até virar `SYNCED` (ou investigar se não
     convergir).
   - O agendamento aparece no painel Darwin no horário correto.

5. **Reenviar a mesma requisição do passo 4 (mesma Idempotency-Key, mesmo corpo)**
   Confirmar que a resposta é `201` (ou `200`, conforme implementação) com o
   **mesmo `idLocal`** — prova que o retry não duplicou o agendamento na Darwin
   real (não apenas no mock).

6. **Criar um encaixe de teste**
   `POST /api/n8n/agenda/encaixe` com uma `Idempotency-Key` diferente da do
   passo 4, no mesmo horário do agendamento criado (para provar que o encaixe
   realmente sobrepõe). Confirmar `201` e que ambos os registros aparecem no
   painel Darwin no mesmo horário.

7. **Reagendar o agendamento de teste**
   `PUT /api/n8n/agenda/{idLocal do passo 4}` alterando `data`/`horarioInicio`
   para outro horário livre. Confirmar `200` e que o painel Darwin reflete o
   novo horário (não duplicou, apenas moveu).

8. **Cancelar ambos os registros de teste**
   `DELETE /api/n8n/agenda/{id}?motivo=Smoke test de endurecimento — remover`
   para o agendamento reagendado (passo 7) e para o encaixe (passo 6).
   Confirmar `204` e que ambos aparecem como cancelados/removidos no painel
   Darwin.

9. **Limpeza final**
   Confirmar com a equipe/painel Darwin que nenhum registro de teste
   permanece ativo na agenda real da clínica. Se o paciente de teste não for
   reaproveitável para testes futuros, solicitar arquivamento à equipe Darwin
   (o `DarwinClient` desta aplicação não expõe exclusão de paciente).

## Critério de aceite

Só declarar "CRUD Darwin validado em produção" quando **todos** os 9 passos
acima tiverem sido executados manualmente, com evidência (prints do painel
Darwin ou logs correlacionados) anexada ao relatório de deploy. Uma falha em
qualquer passo bloqueia a autorização de uso em produção até a causa ser
entendida — não repetir "na tentativa" sem entender por que falhou.

## Fora de escopo deste roteiro

- Não cobre teste de carga/concorrência real contra a Darwin.
- Não substitui os testes de contrato automatizados (`DarwinClientTest`,
  `DarwinAgendaProviderTest`) — este roteiro assume que eles já passam.
- Não deve ser automatizado como parte do pipeline de CI (grava dados reais na
  Darwin) — é deliberadamente manual, único, e sob supervisão humana.
