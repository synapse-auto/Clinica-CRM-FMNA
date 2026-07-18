import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';
import { AgendaClient, groupAppointmentsForAgenda } from './AgendaClient';

const options = {
  pacientes: [{ id: 20, nome: 'Maria da Silva', codigoExterno: null, origem: 'CRM' }],
  medicos: [{ id: 30, nome: 'Dra. Renata', codigoExterno: null, origem: 'CRM' }],
};

const existingAppointment = {
  id: 40,
  pacienteId: 20,
  pacienteNome: 'Maria da Silva',
  medicoId: 30,
  medicoNome: 'Dra. Renata',
  medicoExternalId: null,
  medicoOrigem: 'CRM',
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

function appointment(overrides: Partial<typeof existingAppointment> = {}) {
  return {
    ...existingAppointment,
    ...overrides,
  };
}

const medwareOptions = {
  pacientes: [],
  medicos: [
    { id: null, nome: 'Médico Fictício A', codigoExterno: '101', origem: 'MEDWARE' },
    { id: null, nome: 'Médico Fictício B', codigoExterno: '102', origem: 'MEDWARE' },
  ],
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
    const fetchMock = vi.fn((path: string) => Promise.resolve({
      ok: true,
      json: async () => path.includes('/opcoes') ? options : [julyAppointment],
    }));
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

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(decodeURIComponent(String(fetchMock.mock.calls[0][0]))).toContain('/api/agendamentos?inicio=');
    expect(decodeURIComponent(String(fetchMock.mock.calls[0][0]))).toContain('&fim=');
    expect(await screen.findByText('Paciente Julho')).toBeInTheDocument();
    expect(screen.getByText('Agendamentos de 01/07/2026 a 31/07/2026')).toBeInTheDocument();
    expect(screen.getByText('Total no período')).toBeInTheDocument();
  });

  it('should_load_previous_month_period', async () => {
    const fetchMock = vi.fn((path: string) => Promise.resolve({
      ok: true,
      json: async () => path.includes('/opcoes') ? options : [existingAppointment],
    }));
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

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('Maria da Silva')).toBeInTheDocument();
    expect(screen.getByText('Agendamentos de 01/06/2026 a 30/06/2026')).toBeInTheDocument();
  });

  it('should_apply_custom_period_and_replace_previous_groups', async () => {
    const nextAppointments = [
      appointment({ id: 81, pacienteId: 91, pacienteNome: 'Paciente Novo', dataHoraInicio: '2026-07-15T08:15:00-03:00', servicoNome: 'Procedimento A' }),
      appointment({ id: 82, pacienteId: 91, pacienteNome: 'Paciente Novo', dataHoraInicio: '2026-07-15T08:15:00-03:00', servicoNome: 'Procedimento B' }),
    ];
    const fetchMock = vi.fn((path: string) => Promise.resolve({
      ok: true,
      json: async () => path.includes('/opcoes') ? options : nextAppointments,
    }));
    vi.stubGlobal('fetch', fetchMock);

    render(
      <AgendaClient
        initialAppointments={[appointment({
          id: 80,
          pacienteNome: 'Paciente Anterior',
          dataHoraInicio: '2026-07-13T09:00:00-03:00',
        })]}
        initialOptions={options}
        initialError={null}
        weekStart="2026-07-06"
      />,
    );

    fireEvent.click(screen.getByLabelText(/início/i));
    fireEvent.click(screen.getByRole('button', { name: /13 de julho/i }));
    fireEvent.click(screen.getByLabelText(/fim/i));
    fireEvent.click(screen.getByRole('button', { name: /17 de julho/i }));
    fireEvent.click(screen.getByRole('button', { name: /aplicar período/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    const appointmentsUrl = decodeURIComponent(String(fetchMock.mock.calls[0][0]));
    expect(appointmentsUrl).toContain('inicio=2026-07-13T00:00:00-03:00');
    expect(appointmentsUrl).toContain('fim=2026-07-18T00:00:00-03:00');
    expect(screen.queryByText('Paciente Anterior')).not.toBeInTheDocument();
    expect(await screen.findAllByText('Paciente Novo')).toHaveLength(1);
    expect(screen.getByText('2 procedimentos')).toBeInTheDocument();
    expect(screen.getByText('Agendamentos de 13/07/2026 a 17/07/2026')).toBeInTheDocument();
  });

  it('should_keep_current_week_shortcut_and_switch_between_compact_days', async () => {
    const weekAppointments = [
      appointment({ id: 83, pacienteNome: 'Paciente Segunda', dataHoraInicio: '2026-07-13T08:15:00-03:00' }),
      appointment({ id: 84, pacienteNome: 'Paciente Quarta', dataHoraInicio: '2026-07-15T17:45:00-03:00' }),
    ];
    const fetchMock = vi.fn((path: string) => Promise.resolve({
      ok: true,
      json: async () => path.includes('/opcoes') ? options : weekAppointments,
    }));
    vi.stubGlobal('fetch', fetchMock);

    render(
      <AgendaClient
        initialAppointments={[]}
        initialOptions={options}
        initialError={null}
        weekStart="2026-07-13"
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: /semana atual/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    const appointmentsUrl = decodeURIComponent(String(fetchMock.mock.calls[0][0]));
    expect(appointmentsUrl).toContain('inicio=2026-07-13T00:00:00-03:00');
    expect(appointmentsUrl).toContain('fim=2026-07-18T00:00:00-03:00');
    expect(await screen.findByText('Paciente Segunda')).toBeInTheDocument();
    expect(screen.queryByText('Paciente Quarta')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /seg.*13.*1 agendamento/i })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: /ter.*14.*0 agendamentos/i })).toBeInTheDocument();
    const wednesday = screen.getByRole('button', { name: /qua.*15.*1 agendamento/i });
    fireEvent.click(wednesday);
    expect(screen.queryByText('Paciente Segunda')).not.toBeInTheDocument();
    expect(screen.getByText('Paciente Quarta')).toBeInTheDocument();
    expect(wednesday).toHaveAttribute('aria-pressed', 'true');

    fireEvent.click(screen.getByRole('button', { name: /ter.*14.*0 agendamentos/i }));
    expect(screen.getByText('Sem agendamentos')).toBeInTheDocument();
  });

  it('should_group_four_procedures_for_same_patient_time_doctor_and_status', () => {
    const appointments = [
      appointment({ id: 101, pacienteId: 201, pacienteNome: 'Paciente Fictício', medicoId: null, medicoNome: 'Médico Fictício A', medicoExternalId: '101', medicoOrigem: 'MEDWARE', dataHoraInicio: '2026-07-15T08:15:00-03:00', servicoNome: 'Procedimento Delta' }),
      appointment({ id: 102, pacienteId: 201, pacienteNome: 'Paciente Fictício', medicoId: null, medicoNome: 'Médico Fictício A', medicoExternalId: '101', medicoOrigem: 'MEDWARE', dataHoraInicio: '2026-07-15T08:15:00-03:00', servicoNome: 'Procedimento Alfa' }),
      appointment({ id: 103, pacienteId: 201, pacienteNome: 'Paciente Fictício', medicoId: null, medicoNome: 'Médico Fictício A', medicoExternalId: '101', medicoOrigem: 'MEDWARE', dataHoraInicio: '2026-07-15T08:15:00-03:00', servicoNome: 'Procedimento Gama' }),
      appointment({ id: 104, pacienteId: 201, pacienteNome: 'Paciente Fictício', medicoId: null, medicoNome: 'Médico Fictício A', medicoExternalId: '101', medicoOrigem: 'MEDWARE', dataHoraInicio: '2026-07-15T08:15:00-03:00', servicoNome: 'Procedimento Beta' }),
    ];

    render(
      <AgendaClient
        initialAppointments={appointments}
        initialOptions={medwareOptions}
        initialError={null}
        weekStart="2026-07-13"
      />,
    );

    expect(screen.getAllByText('Paciente Fictício')).toHaveLength(1);
    expect(screen.getByText('4 procedimentos')).toBeInTheDocument();
    expect(screen.getAllByText('Procedimento Alfa').length).toBeGreaterThan(1);
    const groupButton = screen.getByLabelText(/Paciente Fictício/);
    expect(groupButton).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(groupButton);
    expect(screen.getAllByText('Procedimento Alfa').length).toBeGreaterThan(2);
    expect(screen.getAllByText('Procedimento Beta').length).toBeGreaterThan(2);
    expect(screen.getAllByText('Procedimento Gama').length).toBeGreaterThan(2);
    expect(screen.getAllByText('Procedimento Delta').length).toBeGreaterThan(2);
    expect(groupButton).toHaveAttribute('aria-expanded', 'true');
    expect(groupButton).toHaveAttribute(
      'data-appointment-ids',
      '102,104,101,103',
    );
    fireEvent.click(groupButton);
    expect(screen.getAllByText('Procedimento Alfa').length).toBeGreaterThan(1);
  });

  it('should_keep_groups_separate_by_doctor_status_and_patient', () => {
    const base = {
      medicoId: null,
      medicoOrigem: 'MEDWARE',
      dataHoraInicio: '2026-07-15T08:15:00-03:00',
    };
    const appointments = [
      appointment({ ...base, id: 201, pacienteId: 301, pacienteNome: 'Paciente Um', medicoNome: 'Médico Fictício A', medicoExternalId: '101', status: 'AGENDADO', servicoNome: 'Procedimento A' }),
      appointment({ ...base, id: 202, pacienteId: 301, pacienteNome: 'Paciente Um', medicoNome: 'Médico Fictício B', medicoExternalId: '102', status: 'AGENDADO', servicoNome: 'Procedimento B' }),
      appointment({ ...base, id: 203, pacienteId: 301, pacienteNome: 'Paciente Um', medicoNome: 'Médico Fictício A', medicoExternalId: '101', status: 'CANCELADO', servicoNome: 'Procedimento C' }),
      appointment({ ...base, id: 204, pacienteId: 302, pacienteNome: 'Paciente Dois', medicoNome: 'Médico Fictício A', medicoExternalId: '101', status: 'AGENDADO', servicoNome: 'Procedimento D' }),
    ];

    const groups = groupAppointmentsForAgenda(appointments);

    expect(groups).toHaveLength(4);
    expect(groups.map((group) => group.appointmentIds)).toEqual([[204], [201], [202], [203]]);
  });

  it('should_render_single_procedure_without_multiple_procedures_label', () => {
    render(
      <AgendaClient
        initialAppointments={[appointment({
          id: 301,
          dataHoraInicio: '2026-07-15T17:45:00-03:00',
          servicoNome: 'Procedimento Único',
        })]}
        initialOptions={options}
        initialError={null}
        weekStart="2026-07-13"
      />,
    );

    expect(screen.getAllByText('Procedimento Único').length).toBeGreaterThan(1);
    expect(screen.queryByText(/procedimentos$/i)).not.toBeInTheDocument();
    expect(screen.getByText(/17:45/)).toBeInTheDocument();
  });

  it('should_filter_groups_by_medware_doctor', () => {
    const appointments = [
      appointment({ id: 401, pacienteNome: 'Paciente Médico A', medicoId: null, medicoNome: 'Médico Fictício A', medicoExternalId: '101', medicoOrigem: 'MEDWARE', dataHoraInicio: '2026-07-15T08:15:00-03:00', servicoNome: 'Procedimento A' }),
      appointment({ id: 402, pacienteNome: 'Paciente Médico B', medicoId: null, medicoNome: 'Médico Fictício B', medicoExternalId: '102', medicoOrigem: 'MEDWARE', dataHoraInicio: '2026-07-15T08:15:00-03:00', servicoNome: 'Procedimento B' }),
    ];
    render(
      <AgendaClient
        initialAppointments={appointments}
        initialOptions={medwareOptions}
        initialError={null}
        weekStart="2026-07-13"
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Médico Fictício A' }));

    expect(screen.getByText('Paciente Médico A')).toBeInTheDocument();
    expect(screen.queryByText('Paciente Médico B')).not.toBeInTheDocument();
  });

  it('should_filter_patients_without_fetching_and_combine_with_doctor', () => {
    const appointments = [
      appointment({ id: 451, pacienteNome: 'Ana Lara Lopes Ferreira', medicoId: 30, medicoNome: 'Dra. Renata', dataHoraInicio: '2026-07-15T08:15:00-03:00' }),
      appointment({ id: 452, pacienteNome: 'Jo\u00e3o Souza', medicoId: 31, medicoNome: 'Dr. Paulo', dataHoraInicio: '2026-07-15T09:00:00-03:00' }),
    ];
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(
      <AgendaClient
        initialAppointments={appointments}
        initialOptions={{ ...options, medicos: [...options.medicos, { id: 31, nome: 'Dr. Paulo', codigoExterno: null, origem: 'CRM' }] }}
        initialError={null}
        weekStart="2026-07-13"
      />,
    );

    const search = screen.getByRole('searchbox', { name: 'Buscar paciente na agenda' });
    fireEvent.change(search, { target: { value: '  ferreira   ana ' } });
    expect(screen.getByText('Ana Lara Lopes Ferreira')).toBeInTheDocument();
    expect(screen.queryByText('Jo\u00e3o Souza')).not.toBeInTheDocument();
    expect(screen.getByText('1 agendamentos encontrados')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();

    fireEvent.keyDown(search, { key: 'Escape' });
    expect(screen.getByText('Jo\u00e3o Souza')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Dra. Renata' }));
    fireEvent.change(search, { target: { value: 'joao' } });
    expect(screen.getByText('Nenhum agendamento encontrado para este paciente no per\u00edodo selecionado.')).toBeInTheDocument();
  });

  it('should_preserve_sao_paulo_time_and_show_empty_selected_day', () => {
    render(
      <AgendaClient
        initialAppointments={[
          appointment({ id: 501, dataHoraInicio: '2026-07-15T08:15:00-03:00', servicoNome: 'Procedimento Manhã' }),
          appointment({ id: 502, dataHoraInicio: '2026-07-15T17:45:00-03:00', servicoNome: 'Procedimento Tarde' }),
        ]}
        initialOptions={options}
        initialError={null}
        weekStart="2026-07-13"
      />,
    );

    expect(screen.getByText(/08:15/)).toBeInTheDocument();
    expect(screen.getByText(/17:45/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /qua.*15.*2 agendamentos/i })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByRole('button', { name: /seg.*13.*0 agendamentos/i }));
    expect(screen.getByText('Sem agendamentos')).toBeInTheDocument();
  });

  it('should_preserve_all_ids_when_raw_records_are_visually_grouped', () => {
    const appointments = Array.from({ length: 62 }, (_, index) => appointment({
      id: 600 + index,
      pacienteId: 700 + Math.floor(index / 2),
      pacienteNome: `Paciente Fictício ${Math.floor(index / 2)}`,
      medicoId: null,
      medicoNome: 'Médico Fictício A',
      medicoExternalId: '101',
      medicoOrigem: 'MEDWARE',
      dataHoraInicio: '2026-07-15T08:15:00-03:00',
      servicoNome: `Procedimento ${index % 2 === 0 ? 'A' : 'B'}`,
    }));

    const groups = groupAppointmentsForAgenda(appointments);
    const groupedIds = groups.flatMap((group) => group.appointmentIds);

    expect(groups.length).toBeLessThan(appointments.length);
    expect(groupedIds).toHaveLength(62);
    expect(new Set(groupedIds).size).toBe(62);
    expect(groupedIds.sort((left, right) => left - right)).toEqual(
      appointments.map((item) => item.id),
    );

    render(
      <AgendaClient
        initialAppointments={appointments}
        initialOptions={medwareOptions}
        initialError={null}
        weekStart="2026-07-13"
      />,
    );

    expect(screen.getByRole('button', { name: /qua.*15.*62 agendamentos/i })).toBeInTheDocument();
  });

  it('should_prefer_today_when_it_is_in_range_and_has_appointments', () => {
    vi.setSystemTime(new Date('2026-07-15T12:00:00-03:00'));
    render(
      <AgendaClient
        initialAppointments={[
          appointment({ id: 701, pacienteNome: 'Paciente Segunda', dataHoraInicio: '2026-07-13T08:15:00-03:00' }),
          appointment({ id: 702, pacienteNome: 'Paciente Hoje', dataHoraInicio: '2026-07-15T09:30:00-03:00' }),
        ]}
        initialOptions={options}
        initialError={null}
        weekStart="2026-07-13"
      />,
    );

    expect(screen.getByText('Paciente Hoje')).toBeInTheDocument();
    expect(screen.queryByText('Paciente Segunda')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /qua.*15.*1 agendamento/i })).toHaveAttribute('aria-pressed', 'true');
  });

  it('should_distribute_real_services_independently_by_doctor', () => {
    const appointments = [
      appointment({
        id: 801,
        medicoId: 1,
        medicoNome: 'Medico A',
        dataHoraInicio: '2026-07-15T08:15:00-03:00',
        servicoNome: 'Ultrassonografia',
      }),
      appointment({
        id: 802,
        medicoId: 1,
        medicoNome: 'Medico A',
        dataHoraInicio: '2026-07-15T09:15:00-03:00',
        servicoNome: 'Ultrassonografia',
      }),
      appointment({
        id: 803,
        medicoId: 2,
        medicoNome: 'Medico B',
        dataHoraInicio: '2026-07-15T10:15:00-03:00',
        servicoNome: 'Consulta ginecologica',
      }),
    ];

    render(
      <AgendaClient
        initialAppointments={appointments}
        initialOptions={{
          pacientes: [],
          medicos: [
            { id: 1, nome: 'Medico A', codigoExterno: null, origem: 'CRM' },
            { id: 2, nome: 'Medico B', codigoExterno: null, origem: 'CRM' },
          ],
        }}
        initialError={null}
        weekStart="2026-07-13"
      />,
    );

    expect(screen.getByLabelText('Medico A, Ultrassonografia: 2')).toBeInTheDocument();
    expect(screen.getByLabelText('Medico B, Ultrassonografia: 0')).toBeInTheDocument();
    expect(screen.getByLabelText('Medico A, Consulta ginecologica: 0')).toBeInTheDocument();
    expect(screen.getByLabelText('Medico B, Consulta ginecologica: 1')).toBeInTheDocument();
  });
});
