import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { AgendaClient } from './AgendaClient';
import {
  cancelAgendamento,
  createAgendamento,
  updateAgendamento,
} from '@/services/agendamentos';

vi.mock('@/services/agendamentos', () => ({
  createAgendamento: vi.fn(),
  updateAgendamento: vi.fn(),
  cancelAgendamento: vi.fn(),
}));

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
  dataHoraInicio: '2026-06-22T09:00:00-03:00',
  dataHoraFim: '2026-06-22T09:30:00-03:00',
  tipo: 'CONSULTA',
  servicoNome: 'Pré-natal',
  status: 'AGENDADO',
  origem: 'MANUAL',
  confirmacaoEnviada: 0,
  canceladoEm: null,
  motivoCancelamento: null,
};

describe('AgendaClient', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_create_appointment_and_update_week_grid', async () => {
    const user = userEvent.setup();
    vi.mocked(createAgendamento).mockResolvedValue({
      ...existingAppointment,
      id: 41,
      pacienteNome: 'Maria da Silva',
      dataHoraInicio: '2026-06-23T10:00:00-03:00',
      dataHoraFim: '2026-06-23T10:30:00-03:00',
    });
    render(
      <AgendaClient
        initialAppointments={[]}
        initialOptions={options}
        initialError={null}
        weekStart="2026-06-22"
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Novo agendamento' }));
    await user.selectOptions(screen.getByLabelText('Paciente'), '20');
    await user.selectOptions(screen.getByLabelText('Médico ou profissional'), '30');
    fireEvent.change(screen.getByLabelText('Data'), { target: { value: '2026-06-23' } });
    fireEvent.change(screen.getByLabelText('Horário inicial'), { target: { value: '10:00' } });
    fireEvent.change(screen.getByLabelText('Horário final'), { target: { value: '10:30' } });
    await user.selectOptions(screen.getByLabelText('Tipo'), 'CONSULTA');
    await user.type(screen.getByLabelText('Procedimento'), 'Pré-natal');
    await user.click(screen.getByRole('button', { name: 'Salvar agendamento' }));

    await waitFor(() => expect(createAgendamento).toHaveBeenCalledTimes(1));
    expect(screen.getByText('Maria da Silva')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should_cancel_appointment_logically_and_keep_it_visible', async () => {
    const user = userEvent.setup();
    vi.mocked(cancelAgendamento).mockResolvedValue({
      ...existingAppointment,
      status: 'CANCELADO',
      canceladoEm: '2026-06-18T12:00:00-03:00',
      motivoCancelamento: 'Paciente solicitou remarcação',
    });
    render(
      <AgendaClient
        initialAppointments={[existingAppointment]}
        initialOptions={options}
        initialError={null}
        weekStart="2026-06-22"
      />,
    );

    await user.click(screen.getByRole('button', { name: /Maria da Silva/ }));
    await user.click(screen.getByRole('button', { name: 'Cancelar agendamento' }));
    await user.type(screen.getByLabelText('Motivo do cancelamento'), 'Paciente solicitou remarcação');
    await user.click(screen.getByRole('button', { name: 'Confirmar cancelamento' }));

    await waitFor(() => expect(cancelAgendamento).toHaveBeenCalledWith(
      40,
      'Paciente solicitou remarcação',
    ));
    expect(screen.getByRole('button', { name: /Maria da Silva.*Cancelado/ })).toBeInTheDocument();
  });

  it('should_show_visible_error_when_api_request_fails', async () => {
    const user = userEvent.setup();
    vi.mocked(updateAgendamento).mockRejectedValue(new Error('Backend indisponível'));
    render(
      <AgendaClient
        initialAppointments={[existingAppointment]}
        initialOptions={options}
        initialError={null}
        weekStart="2026-06-22"
      />,
    );

    await user.click(screen.getByRole('button', { name: /Maria da Silva/ }));
    await user.click(screen.getByRole('button', { name: 'Salvar agendamento' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Backend indisponível');
  });
});
