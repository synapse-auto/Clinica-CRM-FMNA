export const demoAgendaResumo = {
  consultasHoje: 7,
  totalSemana: 81,
  medicosDisponiveis: 4,
  taxaOcupacao: 78,
};

export const demoAgendaSemanal = [
  {
    dia: 'Seg',
    agendamentos: [
      { paciente: 'Beatriz', horario: '10:00', medico: 'Dra. Renata', tone: 'blue' },
      { paciente: 'Isabela', horario: '09:00', medico: 'Dra. Renata', tone: 'blue' },
      { paciente: 'Gabriela', horario: '09:00', medico: 'Dra. Renata', tone: 'blue' },
    ],
  },
  {
    dia: 'Ter',
    agendamentos: [
      { paciente: 'Camila', horario: '10:00', medico: 'Dr. Carlos', tone: 'blue' },
      { paciente: 'Larissa', horario: '14:00', medico: 'Dr. Carlos', tone: 'blue' },
    ],
  },
  {
    dia: 'Qua',
    agendamentos: [
      { paciente: 'Rafaela', horario: '11:00', medico: 'Dra. Patrícia', tone: 'green' },
    ],
  },
  {
    dia: 'Qui',
    agendamentos: [
      { paciente: 'Patrícia', horario: '15:30', medico: 'Dr. Roberto', tone: 'green' },
      { paciente: 'Monique', horario: '10:00', medico: 'Dr. Roberto', tone: 'blue' },
    ],
  },
  {
    dia: 'Sex',
    agendamentos: [
      { paciente: 'Juliana', horario: '14:00', medico: 'Dra. Renata', tone: 'blue' },
      { paciente: 'Thaís', horario: '09:00', medico: 'Dra. Renata', tone: 'blue' },
    ],
  },
];

export const demoTiposAtendimento = [
  { label: 'Consultas', value: 49, color: 'var(--clinic-primary)' },
  { label: 'Exames', value: 25, color: 'var(--clinic-cyan)' },
  { label: 'Cirurgias', value: 7, color: 'var(--clinic-indigo)' },
  { label: 'Retornos', value: 14, color: 'var(--clinic-orange)' },
];

export const demoAgendaPorMedico = {
  labels: ['Dra. Renata', 'Dr. Carlos', 'Dra. Patrícia', 'Dr. Roberto'],
  series: [
    { label: 'Consultas', color: 'var(--clinic-primary)', values: [18, 12, 15, 10] },
    { label: 'Exames', color: 'var(--clinic-orange)', values: [7, 5, 3, 4] },
    { label: 'Cirurgias', color: 'var(--clinic-indigo)', values: [4, 2, 0, 0] },
  ],
};

export const demoPacientesResumo = {
  total: 27,
  receitaTotal: 'R$ 110.450',
  status: {
    emAtendimento: 7,
    agendado: 8,
    followUp: 3,
    finalizado: 9,
  },
};

export type DemoPaciente = {
  id: number;
  nome: string;
  email: string;
  telefone: string;
  status: 'Em Atendimento' | 'Agendado' | 'Follow UP' | 'Finalizado';
  tags: string[];
  total: string;
  atendente: string;
  atendenteIniciais: string;
  atendenteTone: 'green' | 'blue' | 'pink' | 'orange' | 'purple';
};

export const demoPacientes: DemoPaciente[] = [
  {
    id: 1,
    nome: 'Alessandra Borges Pinto',
    email: 'alessandra.borges@outlook.com',
    telefone: '44 9 9778-4455',
    status: 'Finalizado',
    tags: ['Cirurgia', 'Particular'],
    total: 'R$ 11.200',
    atendente: 'Dra.',
    atendenteIniciais: 'RF',
    atendenteTone: 'green',
  },
  {
    id: 2,
    nome: 'Aline Ferreira Nascimento',
    email: 'aline.nascimento@gmail.com',
    telefone: '44 9 9590-6677',
    status: 'Finalizado',
    tags: ['Consulta Pré-natal', 'Plano de Saúde'],
    total: 'R$ 1.240',
    atendente: 'Marcos',
    atendenteIniciais: 'MC',
    atendenteTone: 'blue',
  },
  {
    id: 3,
    nome: 'Ana Paula Mendes',
    email: 'ana.mendes@yahoo.com',
    telefone: '44 9 9267-5544',
    status: 'Em Atendimento',
    tags: ['Cirurgia', 'Plano de Saúde', 'Alta Prioridade'],
    total: 'R$ 18.600',
    atendente: 'Dra.',
    atendenteIniciais: 'RF',
    atendenteTone: 'green',
  },
  {
    id: 4,
    nome: 'Beatriz Lima Ferreira',
    email: 'sem e-mail',
    telefone: '44 9 9534-7788',
    status: 'Em Atendimento',
    tags: ['Consulta Pré-natal', 'Plano de Saúde', 'Novo Paciente'],
    total: 'R$ 1.850',
    atendente: 'Marcos',
    atendenteIniciais: 'MC',
    atendenteTone: 'blue',
  },
  {
    id: 5,
    nome: 'Camila Rodrigues Oliveira',
    email: 'camila.rodrigues@gmail.com',
    telefone: '44 9 9612-5544',
    status: 'Em Atendimento',
    tags: ['Consulta Pré-natal', 'Plano de Saúde'],
    total: 'R$ 250',
    atendente: 'Fernanda',
    atendenteIniciais: 'FS',
    atendenteTone: 'pink',
  },
  {
    id: 6,
    nome: 'Cristina Alves Barbosa',
    email: 'cristina.barbosa@gmail.com',
    telefone: '44 9 9801-4433',
    status: 'Finalizado',
    tags: ['Cirurgia', 'Particular', 'VIP'],
    total: 'R$ 9.400',
    atendente: 'Dra.',
    atendenteIniciais: 'RF',
    atendenteTone: 'green',
  },
  {
    id: 7,
    nome: 'Débora Santos Vieira',
    email: 'debora.vieira@hotmail.com',
    telefone: '44 9 9956-7744',
    status: 'Agendado',
    tags: ['Cirurgia', 'Plano de Saúde', 'Novo Paciente'],
    total: 'R$ 560',
    atendente: 'Dra.',
    atendenteIniciais: 'RF',
    atendenteTone: 'green',
  },
  {
    id: 8,
    nome: 'Fernanda Oliveira Costa',
    email: 'fernanda.oliveira@gmail.com',
    telefone: '44 9 9356-6699',
    status: 'Em Atendimento',
    tags: ['Cirurgia', 'Alta Prioridade', 'Plano de Saúde'],
    total: 'R$ 12.400',
    atendente: 'Dra.',
    atendenteIniciais: 'RF',
    atendenteTone: 'green',
  },
  {
    id: 9,
    nome: 'Gabriela Torres Alves',
    email: 'gabriela.torres@gmail.com',
    telefone: '44 9 9301-5544',
    status: 'Agendado',
    tags: ['Consulta Pré-natal', 'Novo Paciente'],
    total: '—',
    atendente: 'Ana',
    atendenteIniciais: 'AL',
    atendenteTone: 'orange',
  },
  {
    id: 10,
    nome: 'Isabela Martins Campos',
    email: 'isabela.martins@gmail.com',
    telefone: '44 9 9881-6633',
    status: 'Follow UP',
    tags: ['Alta Prioridade', 'Plano de Saúde', 'Follow-up'],
    total: '—',
    atendente: 'Fernanda',
    atendenteIniciais: 'FS',
    atendenteTone: 'pink',
  },
  {
    id: 11,
    nome: 'Juliana Costa Rodrigues',
    email: 'juliana.costa@outlook.com',
    telefone: '44 9 9723-8876',
    status: 'Em Atendimento',
    tags: ['Consulta Pré-natal', 'Plano de Saúde', 'Retorno'],
    total: 'R$ 280',
    atendente: 'Ana',
    atendenteIniciais: 'AL',
    atendenteTone: 'orange',
  },
  {
    id: 12,
    nome: 'Karoline Dias Ferreira',
    email: 'karoline.dias@gmail.com',
    telefone: '44 9 9845-2233',
    status: 'Finalizado',
    tags: ['Consulta Pré-natal', 'Plano de Saúde', 'VIP'],
    total: 'R$ 5.800',
    atendente: 'Patrícia',
    atendenteIniciais: 'PM',
    atendenteTone: 'purple',
  },
];

export type DemoEquipePessoa = {
  id: number;
  nome: string;
  iniciais: string;
  funcao: string;
  email?: string;
  telefone?: string;
  status: 'Online' | 'Ocupado' | 'Offline';
  acesso?: string;
  atendimentosAtivos?: number;
  tempoMedio?: string;
  tone: 'teal' | 'blue' | 'purple' | 'cyan' | 'orange' | 'pink';
};

export const demoEquipeGrupos: Array<{
  id: string;
  titulo: string;
  pessoas: DemoEquipePessoa[];
}> = [
  {
    id: 'gestor',
    titulo: 'Gestor',
    pessoas: [
      {
        id: 1,
        nome: 'Dra. Renata Fiuza',
        iniciais: 'RF',
        funcao: 'Diretora Clínica',
        email: 'renata@clinicabemestar.com.br',
        telefone: '44 9 9900-0001',
        status: 'Online',
        acesso: 'Gestor',
        tone: 'teal',
      },
    ],
  },
  {
    id: 'medicos',
    titulo: 'Médicos',
    pessoas: [
      { id: 2, nome: 'Dra. Renata Fiuza', iniciais: 'RF', funcao: 'Obstetrícia & Pré-natal', status: 'Online', acesso: 'Sem acesso ao sistema', tone: 'teal' },
      { id: 3, nome: 'Dr. Carlos Mendes', iniciais: 'CM', funcao: 'Ginecologia', status: 'Online', acesso: 'Sem acesso ao sistema', tone: 'blue' },
      { id: 4, nome: 'Dra. Patrícia Lima', iniciais: 'PL', funcao: 'Ultrassonografia', status: 'Ocupado', acesso: 'Sem acesso ao sistema', tone: 'purple' },
      { id: 5, nome: 'Dr. Roberto Alves', iniciais: 'RA', funcao: 'Clínica Geral', status: 'Offline', acesso: 'Sem acesso ao sistema', tone: 'cyan' },
    ],
  },
  {
    id: 'recepcionistas',
    titulo: 'Recepcionistas',
    pessoas: [
      { id: 6, nome: 'Ana Lima', iniciais: 'AL', funcao: 'Recepcionista', status: 'Online', atendimentosAtivos: 14, tempoMedio: '3,2min', tone: 'teal' },
      { id: 7, nome: 'Marcos Costa', iniciais: 'MC', funcao: 'Recepcionista', status: 'Online', atendimentosAtivos: 8, tempoMedio: '4,8min', tone: 'blue' },
    ],
  },
];

export type DemoTag = {
  id: number;
  nome: string;
  uso: number;
  percentual: number;
  tone: 'blue' | 'red' | 'orange' | 'green' | 'purple' | 'pink' | 'teal' | 'yellow';
};

export const demoTags: DemoTag[] = [
  { id: 1, nome: 'Consulta Pré-natal', uso: 10, percentual: 37, tone: 'blue' },
  { id: 2, nome: 'Alta Prioridade', uso: 4, percentual: 15, tone: 'red' },
  { id: 3, nome: 'Urgente', uso: 0, percentual: 0, tone: 'red' },
  { id: 4, nome: 'Plano de Saúde', uso: 18, percentual: 67, tone: 'orange' },
  { id: 5, nome: 'Particular', uso: 6, percentual: 22, tone: 'green' },
  { id: 6, nome: 'Retorno', uso: 1, percentual: 4, tone: 'orange' },
  { id: 7, nome: 'Novo Paciente', uso: 9, percentual: 33, tone: 'purple' },
  { id: 8, nome: 'Follow-up', uso: 2, percentual: 7, tone: 'pink' },
  { id: 9, nome: 'Cirurgia', uso: 8, percentual: 30, tone: 'teal' },
  { id: 10, nome: 'VIP', uso: 5, percentual: 19, tone: 'yellow' },
];

export type DemoMensagemRapida = {
  id: number;
  titulo: string;
  categoria: string;
  texto: string;
  tone: 'blue' | 'teal' | 'purple' | 'pink';
};

export const demoMensagensRapidas: DemoMensagemRapida[] = [
  {
    id: 1,
    titulo: 'Boas-vindas',
    categoria: 'Abertura',
    texto: 'Olá! Bem-vindo(a) à clínica. Como posso ajudar você hoje? Estamos aqui para cuidar de você com todo carinho!',
    tone: 'blue',
  },
  {
    id: 2,
    titulo: 'Consulta agendada',
    categoria: 'Agendamento',
    texto: 'Sua consulta foi agendada com sucesso! Confirmaremos o horário exato em breve. Lembre-se de trazer documento com foto e carteirinha do plano de saúde.',
    tone: 'teal',
  },
  {
    id: 3,
    titulo: 'Solicitar dados do paciente',
    categoria: 'Agendamento',
    texto: 'Para realizar o agendamento, preciso de algumas informações: nome completo, data de nascimento, número de telefone e plano de saúde.',
    tone: 'teal',
  },
  {
    id: 4,
    titulo: 'Confirmação de cirurgia',
    categoria: 'Cirurgia',
    texto: 'Sua cirurgia foi confirmada! Nossa equipe entrará em contato para passar todas as orientações de preparo. Qualquer dúvida, estamos à disposição!',
    tone: 'purple',
  },
  {
    id: 5,
    titulo: 'Preparo para consulta',
    categoria: 'Orientações',
    texto: 'Para sua consulta, lembre-se de trazer documento com foto, carteirinha do plano de saúde, exames anteriores e chegar 15 minutos antes.',
    tone: 'blue',
  },
  {
    id: 6,
    titulo: 'Planos aceitos',
    categoria: 'Financeiro',
    texto: 'Trabalhamos com os principais planos de saúde e também atendemos particular com formas de pagamento facilitadas. Qual é o seu plano?',
    tone: 'teal',
  },
  {
    id: 7,
    titulo: 'Encerramento',
    categoria: 'Encerramento',
    texto: 'Foi um prazer atender você! Qualquer dúvida sobre consultas, exames ou procedimentos, estamos à disposição. Cuide-se!',
    tone: 'pink',
  },
];

export const demoHorarioIa = {
  atendimento24h: false,
  dias: ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'],
  inicio: '05:00',
  fim: '00:00',
};

export const demoHorariosAtendentes = [
  {
    id: 1,
    nome: 'Ana Lima',
    iniciais: 'AL',
    funcao: 'Recepção',
    dias: ['Seg', 'Ter', 'Qua', 'Qui', 'Sex'],
    inativos: 2,
    status: 'Online',
    tone: 'orange',
  },
  {
    id: 2,
    nome: 'Fernanda Silva',
    iniciais: 'FS',
    funcao: 'Recepção',
    dias: ['Seg', 'Ter', 'Qua', 'Qui', 'Sex'],
    inativos: 2,
    status: 'Online',
    tone: 'pink',
  },
];
