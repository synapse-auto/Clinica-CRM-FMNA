import { AgendaClient } from '@/components/agenda/AgendaClient';
import { getAgendaOptions, getAgendamentos } from '@/services/backend';
import type { Agendamento, AgendaOptions } from '@/types/agendamento';

export default async function AgendaPage() {
  const range = getCurrentWeekRange();
  let appointments: Agendamento[] = [];
  let options: AgendaOptions = { pacientes: [], medicos: [] };
  let error: string | null = null;

  try {
    [appointments, options] = await Promise.all([
      getAgendamentos(range.inicio, range.fim),
      getAgendaOptions(),
    ]);
  } catch {
    error = 'Não foi possível carregar a agenda. Verifique a conexão e tente novamente.';
  }

  return (
    <AgendaClient
      initialAppointments={appointments}
      initialOptions={options}
      initialError={error}
      weekStart={range.weekStart}
    />
  );
}

function getCurrentWeekRange() {
  const nowInSaoPaulo = new Date(new Date().toLocaleString('en-US', {
    timeZone: 'America/Sao_Paulo',
  }));
  const start = new Date(nowInSaoPaulo);
  const day = start.getDay();
  start.setDate(start.getDate() - (day === 0 ? 6 : day - 1));
  const end = new Date(start);
  end.setDate(start.getDate() + 5);
  const weekStart = formatLocalDate(start);
  return {
    weekStart,
    inicio: `${weekStart}T00:00:00-03:00`,
    fim: `${formatLocalDate(end)}T00:00:00-03:00`,
  };
}

function formatLocalDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
