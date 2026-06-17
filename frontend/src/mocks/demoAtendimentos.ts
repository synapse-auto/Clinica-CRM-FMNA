export type DemoConversation = {
  id: number;
  name: string;
  phone: string;
  initials: string;
  time: string;
  preview: string;
  tags: string[];
  owner: string;
  unread?: number;
  mode: 'IA' | 'Humano' | 'Follow UP';
  status: string;
};

export type DemoMessage = {
  id: number;
  direction: 'in' | 'out' | 'system';
  author?: string;
  time: string;
  text: string;
};

export const demoConversations: DemoConversation[] = [
  {
    id: 1,
    name: 'Maria Fernanda Santos',
    phone: '44 9 9801-2345',
    initials: 'MF',
    time: '09:47',
    preview: 'Oi, gostaria de agendar uma consulta...',
    tags: ['Consulta Pre-natal'],
    owner: 'Ana Lima',
    unread: 4,
    mode: 'IA',
    status: 'Agendado',
  },
  {
    id: 2,
    name: 'Juliana Costa Rodrigues',
    phone: '44 9 9920-1134',
    initials: 'JC',
    time: '14:32',
    preview: 'Otimo! Vou estar la na sexta com certeza.',
    tags: ['Consulta Pre-natal'],
    owner: 'IA Mari',
    mode: 'IA',
    status: 'Confirmado',
  },
  {
    id: 3,
    name: 'Camila Rodrigues Oliveira',
    phone: '44 9 9970-5510',
    initials: 'CR',
    time: '16:10',
    preview: 'Combinado! Ate amanha entao.',
    tags: ['Exame', 'Novo Paciente'],
    owner: 'Fernanda',
    mode: 'Humano',
    status: 'Em atendimento',
  },
  {
    id: 4,
    name: 'Sandra Pereira Gomes',
    phone: '44 9 9500-1200',
    initials: 'SP',
    time: '11:20',
    preview: 'Pode confirmar o retorno para dia 28?',
    tags: ['Retorno'],
    owner: 'Paula',
    mode: 'Follow UP',
    status: 'Follow UP',
  },
  {
    id: 5,
    name: 'Fernanda Oliveira Costa',
    phone: '44 9 9188-4420',
    initials: 'FO',
    time: 'Ontem',
    preview: 'Estou muito ansiosa mas confiante...',
    tags: ['Cirurgia'],
    owner: 'Renata',
    unread: 2,
    mode: 'Humano',
    status: 'Prioridade',
  },
];

export const demoMessages: DemoMessage[] = [
  {
    id: 1,
    direction: 'in',
    time: '09:44',
    text: 'Oi, gostaria de agendar uma consulta de pre-natal.',
  },
  {
    id: 2,
    direction: 'system',
    time: '09:44',
    text: 'Atendimento recebido - Ana Lima (Recepcao)',
  },
  {
    id: 3,
    direction: 'out',
    author: 'IA - Mari',
    time: '09:45',
    text: 'Ola, Maria Fernanda! Fico feliz em ajudar com o agendamento. Poderia me informar se possui plano de saude?',
  },
  {
    id: 4,
    direction: 'in',
    time: '09:47',
    text: 'Tenho Unimed e prefiro horario pela manha.',
  },
];
