import { render, screen } from '@testing-library/react';
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

describe('AgendaClient (somente leitura)', () => {
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
});
