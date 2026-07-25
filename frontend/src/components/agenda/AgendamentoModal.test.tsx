import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AgendamentoModal } from './AgendamentoModal';
import type { AgendaAgendamento, AgendaProfissional } from '@/types/agenda';

const services = vi.hoisted(() => ({
  atualizarAgendamento: vi.fn(),
  cancelarAgendamento: vi.fn(),
  criarAgendamento: vi.fn(),
  criarEncaixe: vi.fn(),
  criarOuLocalizarPaciente: vi.fn(),
  listarConvenios: vi.fn(),
  listarHorariosDisponiveis: vi.fn(),
  listarLocais: vi.fn(),
  listarPacientesMedware: vi.fn(),
  listarProcedimentos: vi.fn(),
}));

vi.mock('@/services/agenda', () => services);

const profissionais: AgendaProfissional[] = [
  { id: '10', nome: 'Dra. Ana', origem: 'CRM' },
];

const horario = {
  timetableId: 'tt-1',
  profissionalId: '10',
  profissionalNome: 'Dra. Ana',
  localId: 'loc-1',
  localNome: 'Unidade Centro',
  data: '2026-07-20',
  horarioInicio: '09:00',
  horarioFim: '09:30',
};

const appointment: AgendaAgendamento = {
  idLocal: 55,
  externalId: 'sch-55',
  provider: 'DARWIN',
  pacienteId: 1,
  pacienteNome: 'Maria Teste',
  pacienteCpfMascarado: '***.***.***-**',
  profissionalId: '10',
  profissionalNome: 'Dra. Ana',
  procedimentoId: 'proc-1',
  procedimentoNome: 'Consulta pré-natal',
  convenioId: 'conv-1',
  convenioNome: 'Particular',
  localId: 'loc-1',
  localNome: 'Unidade Centro',
  data: '2026-07-20',
  horarioInicio: '09:00',
  horarioFim: '09:30',
  status: 'AGENDADO',
  timetableId: 'tt-1',
  observacao: null,
  origem: 'DARWIN',
  lastSyncedAt: '2026-07-19T12:00:00Z',
  syncStatus: 'SYNCED',
};

function createdResult(overrides: Partial<AgendaAgendamento> = {}): AgendaAgendamento {
  return { ...appointment, idLocal: 99, ...overrides };
}

describe('AgendamentoModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    services.listarHorariosDisponiveis.mockResolvedValue([horario]);
    services.listarProcedimentos.mockResolvedValue([{ id: 'proc-1', nome: 'Consulta pré-natal' }]);
    services.listarConvenios.mockResolvedValue([{ id: 'conv-1', nome: 'Particular' }]);
    services.listarLocais.mockResolvedValue([{ id: 'loc-1', nome: 'Unidade Centro' }]);
    services.listarPacientesMedware.mockResolvedValue([]);
  });

  it('should_create_darwin_appointment_after_searching_patient_and_selecting_slot', async () => {
    const user = userEvent.setup();
    services.criarOuLocalizarPaciente.mockResolvedValue({ id: 1, nome: 'Maria Teste', cpfMascarado: '***.***.***-**' });
    services.criarAgendamento.mockResolvedValue(createdResult());
    const onSaved = vi.fn();

    render(
      <AgendamentoModal
        appointment={null}
        profissionais={profissionais}
        hasCatalog
        weekStart="2026-07-20"
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );

    await user.type(screen.getByLabelText('CPF do paciente'), '11144477735');
    await user.type(screen.getByLabelText('Nome do paciente'), 'Maria Teste');
    await user.click(screen.getByRole('button', { name: /buscar\/cadastrar paciente/i }));

    expect(await screen.findByText('Paciente selecionado: Maria Teste')).toBeInTheDocument();
    expect(services.criarOuLocalizarPaciente).toHaveBeenCalledWith(expect.objectContaining({
      cpf: '11144477735',
      nome: 'Maria Teste',
    }));

    await user.click(screen.getByLabelText('Profissional'));
    await user.click(await screen.findByRole('option', { name: 'Dra. Ana' }));

    expect(await screen.findByRole('button', { name: '09:00' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '09:00' }));

    await user.click(screen.getByRole('button', { name: /salvar agendamento/i }));

    await waitFor(() => expect(services.criarAgendamento).toHaveBeenCalledTimes(1));
    expect(services.criarAgendamento).toHaveBeenCalledWith(expect.objectContaining({
      pacienteId: 1,
      profissionalId: '10',
      timetableId: 'tt-1',
      horarioInicio: '09:00',
      horarioFim: '09:30',
    }));
    expect(onSaved).toHaveBeenCalledWith(createdResult());
  });

  it('should_show_pending_reconciliation_notice_and_wait_for_acknowledgement', async () => {
    const user = userEvent.setup();
    services.criarOuLocalizarPaciente.mockResolvedValue({ id: 1, nome: 'Maria Teste', cpfMascarado: '***.***.***-**' });
    const pending = createdResult({ syncStatus: 'PENDING_RECONCILIATION' });
    services.criarAgendamento.mockResolvedValue(pending);
    const onSaved = vi.fn();

    render(
      <AgendamentoModal
        appointment={null}
        profissionais={profissionais}
        hasCatalog
        weekStart="2026-07-20"
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );

    await user.type(screen.getByLabelText('CPF do paciente'), '11144477735');
    await user.type(screen.getByLabelText('Nome do paciente'), 'Maria Teste');
    await user.click(screen.getByRole('button', { name: /buscar\/cadastrar paciente/i }));
    await screen.findByText('Paciente selecionado: Maria Teste');

    await user.click(screen.getByLabelText('Profissional'));
    await user.click(await screen.findByRole('option', { name: 'Dra. Ana' }));
    await user.click(await screen.findByRole('button', { name: '09:00' }));
    await user.click(screen.getByRole('button', { name: /salvar agendamento/i }));

    expect(await screen.findByText('Agendamento enviado')).toBeInTheDocument();
    expect(onSaved).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: /entendi/i }));
    expect(onSaved).toHaveBeenCalledWith(pending);
  });

  it('should_create_fitin_appointment_with_overlap_warning', async () => {
    const user = userEvent.setup();
    services.criarOuLocalizarPaciente.mockResolvedValue({ id: 1, nome: 'Maria Teste', cpfMascarado: '***.***.***-**' });
    services.criarEncaixe.mockResolvedValue(createdResult());
    const onSaved = vi.fn();

    render(
      <AgendamentoModal
        appointment={null}
        profissionais={profissionais}
        hasCatalog
        fitIn
        weekStart="2026-07-20"
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );

    expect(screen.getByText('Encaixes podem sobrepor horários existentes.')).toBeInTheDocument();

    await user.type(screen.getByLabelText('CPF do paciente'), '11144477735');
    await user.type(screen.getByLabelText('Nome do paciente'), 'Maria Teste');
    await user.click(screen.getByRole('button', { name: /buscar\/cadastrar paciente/i }));
    await screen.findByText('Paciente selecionado: Maria Teste');

    await user.click(screen.getByLabelText('Profissional'));
    await user.click(await screen.findByRole('option', { name: 'Dra. Ana' }));

    await user.click(screen.getByLabelText('Local'));
    await user.click(await screen.findByRole('option', { name: 'Unidade Centro' }));

    await user.click(screen.getByRole('button', { name: /salvar agendamento/i }));

    await waitFor(() => expect(services.criarEncaixe).toHaveBeenCalledTimes(1));
    expect(services.criarEncaixe).toHaveBeenCalledWith(expect.objectContaining({
      pacienteId: 1,
      localId: 'loc-1',
      horarioInicio: '09:00',
    }));
    expect(onSaved).toHaveBeenCalledWith(createdResult());
  });

  it('should_reschedule_existing_appointment_without_changing_patient_or_professional', async () => {
    const user = userEvent.setup();
    const updated = { ...appointment, horarioInicio: '10:00', horarioFim: '10:30' };
    services.atualizarAgendamento.mockResolvedValue(updated);
    const onSaved = vi.fn();

    render(
      <AgendamentoModal
        appointment={appointment}
        profissionais={profissionais}
        hasCatalog
        weekStart="2026-07-20"
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );

    expect(screen.getByText('Maria Teste')).toBeInTheDocument();
    expect(screen.getByText('Dra. Ana')).toBeInTheDocument();
    expect(services.criarOuLocalizarPaciente).not.toHaveBeenCalled();

    await waitFor(() => expect(services.listarHorariosDisponiveis).toHaveBeenCalled());

    await user.click(screen.getByRole('button', { name: /salvar agendamento/i }));

    await waitFor(() => expect(services.atualizarAgendamento).toHaveBeenCalledTimes(1));
    expect(services.atualizarAgendamento).toHaveBeenCalledWith(55, expect.objectContaining({
      data: '2026-07-20',
    }));
    expect(onSaved).toHaveBeenCalledWith(updated);
  });

  it('should_require_cancel_reason_before_confirming_cancellation', async () => {
    const user = userEvent.setup();
    services.cancelarAgendamento.mockResolvedValue(undefined);
    const onSaved = vi.fn();

    render(
      <AgendamentoModal
        appointment={appointment}
        profissionais={profissionais}
        hasCatalog
        initialCancelMode
        weekStart="2026-07-20"
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );

    const confirmButton = screen.getByRole('button', { name: /confirmar cancelamento/i });
    expect(confirmButton).toBeDisabled();

    await user.type(screen.getByLabelText('Motivo do cancelamento'), 'Paciente remarcou por telefone');
    expect(confirmButton).toBeEnabled();

    await user.click(confirmButton);

    await waitFor(() => expect(services.cancelarAgendamento).toHaveBeenCalledWith(
      55,
      'Paciente remarcou por telefone',
    ));
    expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ idLocal: 55, status: 'CANCELADO' }));
  });

  it('should_create_medware_appointment_from_existing_patient_list', async () => {
    const user = userEvent.setup();
    services.listarPacientesMedware.mockResolvedValue([
      { id: 7, nome: 'João Souza', telefone: '44988887777', status: 'EM_ATENDIMENTO', externalSource: null, externalId: null, fotoUrl: null, criadoEm: '2026-01-01T00:00:00Z', ultimaInteracaoEm: null, tags: [] },
    ]);
    const created = createdResult({ provider: 'MEDWARE', syncStatus: 'SYNCED' });
    services.criarAgendamento.mockResolvedValue(created);
    const onSaved = vi.fn();

    render(
      <AgendamentoModal
        appointment={null}
        profissionais={[]}
        hasCatalog={false}
        weekStart="2026-07-20"
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );

    await user.click(await screen.findByLabelText('Paciente'));
    await user.click(await screen.findByRole('option', { name: 'João Souza' }));

    await user.type(screen.getByLabelText('Procedimento'), 'Consulta de retorno');

    await user.click(screen.getByRole('button', { name: /salvar agendamento/i }));

    await waitFor(() => expect(services.criarAgendamento).toHaveBeenCalledTimes(1));
    expect(services.criarAgendamento).toHaveBeenCalledWith(expect.objectContaining({
      pacienteId: 7,
      profissionalId: null,
      procedimentoNome: 'Consulta de retorno',
    }));
    expect(onSaved).toHaveBeenCalledWith(created);
  });

  it('should_disable_submit_button_while_saving_to_prevent_double_click', async () => {
    const user = userEvent.setup();
    services.listarPacientesMedware.mockResolvedValue([
      { id: 7, nome: 'João Souza', telefone: '44988887777', status: 'EM_ATENDIMENTO', externalSource: null, externalId: null, fotoUrl: null, criadoEm: '2026-01-01T00:00:00Z', ultimaInteracaoEm: null, tags: [] },
    ]);
    let resolveCreate: (value: AgendaAgendamento) => void = () => {};
    services.criarAgendamento.mockReturnValue(new Promise((resolve) => { resolveCreate = resolve; }));

    render(
      <AgendamentoModal
        appointment={null}
        profissionais={[]}
        hasCatalog={false}
        weekStart="2026-07-20"
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );

    await user.click(await screen.findByLabelText('Paciente'));
    await user.click(await screen.findByRole('option', { name: 'João Souza' }));

    const submitButton = screen.getByRole('button', { name: /salvar agendamento/i });
    await user.click(submitButton);

    expect(await screen.findByRole('button', { name: /salvando/i })).toBeDisabled();
    expect(services.criarAgendamento).toHaveBeenCalledTimes(1);

    resolveCreate(createdResult());
  });
});
