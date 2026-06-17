export const demoAgenda = [
  { id: 1, horario: '08:00', paciente: 'Maria Fernanda Santos', servico: 'Consulta Pre-natal', medico: 'Dra. Renata', status: 'Confirmado' },
  { id: 2, horario: '09:30', paciente: 'Juliana Costa Rodrigues', servico: 'Ultrassom obstetrico', medico: 'Dr. Marcelo', status: 'Pendente' },
  { id: 3, horario: '11:00', paciente: 'Camila Rodrigues Oliveira', servico: 'Retorno', medico: 'Dra. Renata', status: 'Em atendimento' },
  { id: 4, horario: '14:00', paciente: 'Sandra Pereira Gomes', servico: 'Exame', medico: 'Dr. Marcelo', status: 'Aguardando' },
];

export const demoPacientes = [
  { id: 1, nome: 'Maria Fernanda Santos', telefone: '44 9 9801-2345', origem: 'WhatsApp', status: 'Agendado', ultimoContato: 'Hoje 09:47', tags: ['Pre-natal', 'Novo'] },
  { id: 2, nome: 'Juliana Costa Rodrigues', telefone: '44 9 9920-1134', origem: 'Instagram', status: 'Confirmado', ultimoContato: 'Hoje 14:32', tags: ['Pre-natal'] },
  { id: 3, nome: 'Camila Rodrigues Oliveira', telefone: '44 9 9970-5510', origem: 'Indicacao', status: 'Em atendimento', ultimoContato: 'Hoje 16:10', tags: ['Exame'] },
  { id: 4, nome: 'Sandra Pereira Gomes', telefone: '44 9 9500-1200', origem: 'WhatsApp', status: 'Follow UP', ultimoContato: 'Ontem', tags: ['Retorno'] },
];

export const demoEquipe = [
  { id: 1, nome: 'Dra. Renata Fiuza', perfil: 'GESTOR', funcao: 'Medica responsavel', status: 'Online', permissoes: 'Total' },
  { id: 2, nome: 'Ana Lima', perfil: 'RECEPCIONISTA', funcao: 'Atendimento WhatsApp', status: 'Online', permissoes: 'Atendimentos' },
  { id: 3, nome: 'Dr. Marcelo Vieira', perfil: 'MEDICO', funcao: 'Ultrassonografia', status: 'Em consulta', permissoes: 'Agenda e pacientes' },
];

export const demoTags = [
  { id: 1, nome: 'Consulta Pre-natal', cor: 'blue', uso: 18 },
  { id: 2, nome: 'Retorno', cor: 'orange', uso: 9 },
  { id: 3, nome: 'Cirurgia', cor: 'pink', uso: 4 },
  { id: 4, nome: 'Novo Paciente', cor: 'teal', uso: 22 },
];

export const demoMensagensRapidas = [
  { id: 1, titulo: 'Confirmacao de consulta', categoria: 'Agenda', texto: 'Ola, [Nome]. Sua consulta esta confirmada para [Data] as [Horario].' },
  { id: 2, titulo: 'Pedido de documentos', categoria: 'Cadastro', texto: 'Pode nos enviar documento com foto e carteirinha do convenio?' },
  { id: 3, titulo: 'Agradecimento', categoria: 'Relacionamento', texto: 'Obrigada pelo contato. Ficamos a disposicao.' },
];

export const demoHorarios = [
  { dia: 'Segunda', inicio: '08:00', fim: '18:00', ativo: true },
  { dia: 'Terca', inicio: '08:00', fim: '18:00', ativo: true },
  { dia: 'Quarta', inicio: '08:00', fim: '18:00', ativo: true },
  { dia: 'Quinta', inicio: '08:00', fim: '18:00', ativo: true },
  { dia: 'Sexta', inicio: '08:00', fim: '17:00', ativo: true },
  { dia: 'Sabado', inicio: '08:00', fim: '12:00', ativo: false },
];
