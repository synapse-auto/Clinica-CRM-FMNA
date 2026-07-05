import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';
import { AgendaClient } from './AgendaClient';

const options = {
  pacientes: [{ id: 20, nome: 'Maria da Silva' }],
  medicos: [{ id: 30, nome: 'Dra. Renata' }],
};

const existingAppointment = {
  id: 40,
  pacienteId: 20,
  pacienteNome: 'Maria da Silva',
  medicoId: 30,
  medicoNome: 'Dra. Renata',
  dataHoraInicio: '2026-06-23T09:00:00-03:00',
  dataHoraFim: '2026-06-23T09:30:00-03:00',
  tipo: 'CONSULTA',
  servicoNome: 'Pré-natal',
  status: 'AGENDADO',
  origem: 'INTEGRACAO_EXTERNA',
  confirmacaoEnviada: 0,
  canceladoEm: null,
  motivoCancelamento: null,
};

const julyAppointment = {
  ...existingAppointment,
  id: 41,
  pacienteNome: 'Paciente Julho',
  dataHoraInicio: '2026-07-10T09:00:00-03:00',
  dataHoraFim: '2026-07-10T09:30:00-03:00',
};

describe('AgendaClient (somente leitura)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-07-05T12:00:00-03:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('should_not_show_novo_agendamento_button', () => {
    render(
      <AgendaClient
        initialAppointments={[]}
        initialOptions={options}
        initialError={null}
        weekStart="2026-06-23"
      />,
    );

    expect(
      screen.queryByRole('button', { name: /novo agendamento/i }),
    ).not.toBeInTheDocument();
  });

  it('should_not_render_dialog_or_modal', () => {
    render(
      <AgendaClient
        initialAppointments={[existingAppointment]}
        initialOptions={options}
        initialError={null}
        weekStart="2026-06-23"
      />,
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should_display_appointment_from_api_in_week_grid', () => {
    render(
      <AgendaClient
        initialAppointments={[existingAppointment]}
        initialOptions={options}
        initialError={null}
        weekStart="2026-06-23"
      />,
    );

    expect(screen.getByText('Maria da Silva')).toBeInTheDocument();
  });

  it('should_show_empty_state_when_no_appointments', () => {
    render(
      <AgendaClient
        initialAppointments={[]}
        initialOptions={options}
        initialError={null}
        weekStart="2026-06-23"
      />,
    );

    const emptyMessages = screen.getAllByText('Sem agendamentos');
    // Uma mensagem por coluna do grid (5 dias da semana)
    expect(emptyMessages.length).toBeGreaterThanOrEqual(1);
  });

  it('should_show_error_alert_when_initialError_is_set', () => {
    render(
      <AgendaClient
        initialAppointments={[]}
        initialOptions={{ pacientes: [], medicos: [] }}
        initialError="Não foi possível carregar a agenda."
        weekStart="2026-06-23"
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Não foi possível carregar a agenda.');
  });

  it('should_not_show_error_alert_when_api_returns_empty_list', () => {
    render(
      <AgendaClient
        initialAppointments={[]}
        initialOptions={{ pacientes: [], medicos: [] }}
        initialError={null}
        weekStart="2026-06-23"
      />,
    );

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('should_display_doctor_filter_buttons', () => {
    render(
      <AgendaClient
        initialAppointments={[]}
        initialOptions={options}
        initialError={null}
        weekStart="2026-06-23"
      />,
    );

    expect(screen.getByRole('button', { name: 'Todos' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Dra. Renata' })).toBeInTheDocument();
  });

  it('should_load_current_month_period_and_show_period_label', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [julyAppointment],
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <AgendaClient
        initialAppointments={[]}
        initialOptions={options}
        initialError={null}
        weekStart="2026-07-06"
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: /mês atual/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(decodeURIComponent(String(fetchMock.mock.calls[0][0]))).toContain('/api/agendamentos?inicio=');
    expect(decodeURIComponent(String(fetchMock.mock.calls[0][0]))).toContain('&fim=');
    expect(await screen.findByText('Paciente Julho')).toBeInTheDocument();
    expect(screen.getByText('Agendamentos de 01/07/2026 a 31/07/2026')).toBeInTheDocument();
    expect(screen.getByText('Total no período')).toBeInTheDocument();
  });

  it('should_load_previous_month_period', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [existingAppointment],
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <AgendaClient
        initialAppointments={[]}
        initialOptions={options}
        initialError={null}
        weekStart="2026-07-06"
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: /mês anterior/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(await screen.findByText('Maria da Silva')).toBeInTheDocument();
    expect(screen.getByText('Agendamentos de 01/06/2026 a 30/06/2026')).toBeInTheDocument();
  });
});
