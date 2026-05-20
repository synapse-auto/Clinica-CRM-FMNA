# CLÍNICA FEMINA — CRM

**Especificação de Requisitos do Sistema — CRM para Clínica de Pré-natal**
**Versão**: 1.5
**Conformidade**: LGPD (Lei 13.709/2018)

> **Nota do Projeto**: Este documento especifica os requisitos para o CRM da Clínica Femina, plataforma centrada em atendimento via WhatsApp, automação de relacionamento e gestão de pacientes de pré-natal. A solução prioriza **segurança de dados sensíveis de saúde** (LGPD), atendimento em **tempo real** e **integração com IA** via N8N para agendamentos, lembretes e fidelização.

---

## 1. Requisitos Funcionais (RF)

| ID | Requisito e Funcionalidade | Descrição |
|----|----------------------------|-----------|
| **RF01** | Autenticação | Tela de login com e-mail e senha. Autenticação implementada via **JWT com tokens de expiração controlada**. |
| **RF02** | Controle de Acesso por Perfil | Três perfis distintos: **Gestor** (acesso total a todas as abas), **Recepcionista** (acesso às abas operacionais de atendimento) e **Médico** (visualização restrita). Cada usuário tem login individual — senhas compartilhadas são proibidas. |
| **RF03** | Aba Atendimentos | Interface de chat para conversas com pacientes via WhatsApp. Lista de leads à esquerda com filtros (Todos, IA, Humano) e busca. Painel lateral direito de cada lead exibe: telefone, e-mail, atendente, médico responsável, horário preferencial, tags, histórico de consultas, último procedimento e lembretes. |
| **RF04** | Notificações em Tempo Real | Notificação visual e sonora instantânea no painel quando uma nova mensagem é recebida ou quando há lembrete programado disparado. |
| **RF05** | Mensagens Rápidas (Templates) | CRUD de mensagens rápidas por usuário (não compartilhadas), organizadas por categorias (Abertura, Orçamento, Agendamento, Suporte, Financeiro, Encerramento, etc.). Na aba Atendimentos, são chamadas digitando `/` no campo de mensagem. |
| **RF06** | Lembretes por Lead | Atendente pode criar lembretes vinculados a cada paciente com data, horário e mensagem customizada. No horário programado, o sistema dispara notificação no painel do atendente responsável. |
| **RF07** | Aba Dashboard | Visão operacional com filtros por dia, semana e mês: equipe online, novos pacientes, total de mensagens, consultas agendadas, confirmações pendentes, tempo médio de resposta, pico de mensagens por hora, distribuição de serviços e taxa de fidelização. |
| **RF08** | Aba Agenda | Visão semanal de agendamentos por médico, com totais de consultas, exames, cirurgias e retornos. Exibe taxa de ocupação da capacidade semanal, próximos agendamentos e gráfico de distribuição por profissional. |
| **RF09** | Aba Pacientes (Lista e Kanban) | Banco de dados completo dos pacientes com busca por nome ou telefone. Ao clicar em um paciente, o usuário é direcionado automaticamente para a conversa daquele cliente na aba Atendimentos. Possui filtros por status (Em Atendimento, Agendado, Follow UP, Finalizado) e colunas de contato, tags, valor total e atendente responsável. Alternância entre visualização em lista e Kanban (colunas por status). |
| **RF10** | Aba Equipe | Visualização somente-leitura dos membros: Gestor, Médicos (com especialidade — Obstetrícia & Pré-natal, Ginecologia, Ultrassonografia, Clínica Geral) e Recepcionistas. Exibe métricas operacionais (atendimentos ativos, tempo médio de resposta). |
| **RF11** | Aba Automação | Gerenciamento de réguas de relacionamento automáticas: lembretes 48h, 24h e 2h antes da consulta; confirmação de cirurgia 72h antes; pós-consulta (avaliação); reativação de pacientes inativos; preventivo anual; mensagens em feriados nacionais. Cada toggle e campo de mensagem atualiza variáveis correspondentes no banco de dados. |
| **RF12** | Gerenciamento e Aplicação de Tags | CRUD de tags (criar, editar nome, alterar cor, remover) com contagem de leads associados e percentual relativo. Aplicação de uma ou mais tags a cada paciente nas abas Atendimentos ou Pacientes. |
| **RF13** | Aba Horários | Configuração das janelas de tempo (dia da semana + horário) em que a IA tem permissão para agendar consultas automaticamente. |
| **RF14** | Aba Configurações | Configurações gerais do sistema: identidade visual da clínica, dados de contato, preferências de notificações, tema claro/escuro por usuário e parâmetros gerais do sistema. |
| **RF15** | Integração via WhatsApp | Conexão via **API Oficial do WhatsApp (Meta Cloud API)** com a conta da clínica para envio e recebimento bidirecional de mensagens — texto, áudios gravados pelo atendente, imagens e documentos. |
| **RF16** | Integração com N8N | Webhooks de entrada e saída para orquestração de fluxos no N8N: gatilhos para novos leads, mudanças de status, agendamentos e disparos automáticos da IA. |
| **RF17** | Integração com API Darwin | Consumo de dados via API do Darwin (CRM especializado em clínicas) para importar pacientes, agendamentos e informações clínicas relevantes. A integração é **unidirecional**: o sistema apenas lê dados do Darwin, sem enviar de volta. |
| **RF18** | Histórico de Atendimentos | Persistência completa do histórico de mensagens e atendimentos por paciente, com indicação do atendente responsável em cada interação. |
| **RF19** | Atribuição e Transferência de Atendente | Cada lead possui um atendente principal designado. Conversas podem ser transferidas para outro atendente (médicos entre si, ou recepcionistas) por ação explícita. |
| **RF20** | Pesquisa de Satisfação Pós-Consulta | Após um intervalo configurável do término de cada consulta finalizada, o sistema envia automaticamente ao paciente uma pergunta de avaliação (ex.: "De 0 a 10, qual sua experiência com a clínica?"). As notas recebidas são armazenadas e agregadas para análise da satisfação geral da clínica (média, distribuição e evolução no tempo). |
| **RF21** | Status do Paciente | Cada paciente possui status atual entre: **Em Atendimento**, **Agendado**, **Follow UP** e **Finalizado**. O status alimenta os indicadores do Dashboard e o Kanban. |
| **RF22** | Aba Resumo de Cancelamentos por IA | Aba dedicada à visualização dos cancelamentos de consultas realizados automaticamente pela IA. Exibe lista detalhada (paciente, médico, data/hora original, motivo identificado pela IA e momento do cancelamento) e visão agregada (total no período, principais motivos, distribuição por médico e por serviço) para análise gerencial e ajustes na automação. |

---

## 1.1 Requisitos Funcionais Adicionais (pós-PDF v1.5)

> Requisitos acrescentados após a transcrição do PDF v1.5, durante o detalhamento de spec/plan. Não constam do PDF original.

| ID | Requisito e Funcionalidade | Descrição |
|----|----------------------------|-----------|
| **RF23** | Filtros de Tag e Recepcionista na Aba Atendimentos | A aba Atendimentos deve exibir, **acima da lista de leads** (topo do painel esquerdo), uma barra de filtros com dois controles: filtro por **tag** e filtro por **recepcionista** (atendente responsável). Cada filtro é opcional e pode ser ativado ou deixado no padrão "todos" (sem filtro). Os filtros combinam entre si e com os filtros existentes (Todos/IA/Humano) e a busca textual. Complementa o RF03. |

---

## 2. Requisitos Não Funcionais (RNF)

| ID | Requisito | Descrição |
|----|-----------|-----------|
| **RNF01** | Latência de Tempo Real | Recebimento de novas mensagens e disparo de notificações em até **2 segundos** após o evento no WhatsApp. |
| **RNF02** | Segurança da Informação | Comunicação 100% via **HTTPS** (TLS 1.2+); senhas com hashing **BCrypt** (custo mínimo 10); tokens JWT com expiração; criptografia de banco de dados em repouso (AES-256) e em trânsito. |
| **RNF03** | LGPD | Conformidade total com a Lei Geral de Proteção de Dados (Lei nº 13.709/2018) no tratamento de dados pessoais e **dados sensíveis de saúde**: base legal documentada, **consentimento explícito** do titular para coleta e comunicações (consultas, lembretes, marketing e fidelização), garantia dos **direitos do titular** (acesso, retificação, portabilidade e eliminação), **exportação de dados** em CSV/XLSX para portabilidade, finalidade específica de uso e protocolo de notificação de incidentes. |
| **RNF04** | Disponibilidade | Disponibilidade mínima de **99,5%** mensal, com janela programada de manutenção comunicada com antecedência. |
| **RNF05** | Performance | Carregamento inicial das páginas em até **3 segundos** em conexão padrão; operações de busca em pacientes em até **1 segundo**. |
| **RNF06** | Plataforma Java | Caso a implementação do sistema utilize a linguagem **Java**, a distribuição deve ser o **OpenJDK**. |

---

## 3. Regras de Negócio (RN)

| ID | Regra | Descrição |
|----|-------|-----------|
| **RN01** | Integridade de Status do Paciente | Transições principais do status: *Em Atendimento → Agendado → Finalizado*. O status *Follow UP* pode ser aplicado em paralelo a qualquer etapa do fluxo, para pacientes que requerem acompanhamento contínuo. Pular etapas arbitrariamente não é permitido. |
| **RN02** | Retenção e Soft Delete | Pacientes não podem ser apagados fisicamente do banco; aplica-se *soft delete* preservando o histórico de atendimentos. Exclusão definitiva apenas mediante fluxo formal. |
| **RN03** | Janela de Atuação da IA | A IA só agenda consultas dentro das janelas configuradas na aba Horários. Solicitações fora dessas janelas devem ser encaminhadas para atendimento humano. |
| **RN04** | Mensagens Rápidas Individuais | Mensagens rápidas são configuradas por usuário. Cada atendente vê e edita apenas as suas, mesmo sob o mesmo cargo. |
| **RN05** | Transferência de Atendente | Cada conversa possui um atendente principal designado. A transferência para outro atendente exige ação explícita e fica registrada. |
| **RN06** | Disparo de Lembretes | Todo lembrete criado pelo atendente dispara notificação no painel **somente** do atendente responsável, no horário programado. |
| **RN07** | Gerenciamento de Tags | **Todos os usuários** do sistema podem criar, renomear, excluir e aplicar tags a pacientes. |
| **RN08** | Integridade de Lembretes | Lembretes vinculados a um paciente são preservados mesmo quando o atendimento é transferido para outro atendente — o novo responsável herda os lembretes. |

---

## 4. Posteriormente Talvez (Backlog)

> Itens listados nesta seção estão marcados para avaliação posterior e **não fazem parte do escopo de implementação atual**. Servem como referência para evolução do produto, devendo ser priorizados conforme demanda operacional futura.

| ID | Item | Descrição |
|----|------|-----------|
| **PT01** | Otimização para Dispositivos Móveis | Interface responsiva e otimizada para uso da equipe em smartphones e tablets, mantendo o acesso via navegador. Atualmente o escopo cobre apenas navegador desktop padrão. |
| **PT02** | Funcionalidade Completa de Follow UP | Implementação do **workflow automatizado** de acompanhamento contínuo para pacientes em status Follow UP — réguas de comunicação programadas, lembretes pós-consulta automáticos, reativação de pacientes inativos, monitoramento de retorno e métricas de eficácia. No escopo atual, o Follow UP existe apenas como **rótulo de status manual**; a automação completa do fluxo fica para fase posterior. |

---

*Documento de Especificação de Requisitos — CRM Clínica Femina*
*Versão 1.5 — em conformidade com LGPD (Lei 13.709/2018)*
*Transcrição em Markdown do PDF `Documentacao_Requisitos_ClinicaFemina_CRM 2.pdf`*
