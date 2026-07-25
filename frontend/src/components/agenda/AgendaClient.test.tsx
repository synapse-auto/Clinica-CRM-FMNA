import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';
import { AgendaClient, groupAppointmentsForAgenda } from './AgendaClient';
import type { AgendaAgendamento, AgendaCapabilities, AgendaProfissional } from '@/types/agenda';

const FULL_CAPABILITIES: AgendaCapabilities = {
  provider: 'DARWIN',
  supportsCatalog: true,
  supportsWriteOperations: true,
  supportsFitIn: true,
  supportsClinicWideListing: false,
  supportsPatientLookup: true,
  supportsBackfill: true,
  coverage: 'KNOWN_CRM_PATIENTS_ONLY',
};

const NO_CATALOG_CAPABILITIES: AgendaCapabilities = {
  provider: 'MEDWARE',
  supportsCatalog: false,
  supportsWriteOperations: false,
  supportsFitIn: false,
  supportsClinicWideListing: true,
  supportsPatientLookup: false,
  supportsBackfill: false,
  coverage: 'FULL',
};

const profissionais: AgendaProfissional[] = [
  { id: '30', nome: 'Dra. Renata', origem: 'CRM' },
];

const existingAppointment: AgendaAgendamento = {
  idLocal: 40,
  externalId: 'ext-40',
  provider: 'MEDWARE',
  pacienteId: 20,
  pacienteNome: 'Maria da Silva',
  pacienteCpfMascarado: null,
  profissionalId: '30',
  profissionalNome: 'Dra. Renata',
  procedimentoId: null,
  procedimentoNome: 'Pré-natal',
  convenioId: null,
  convenioNome: null,
  localId: null,
  localNome: null,
  data: '2026-06-23',
  horarioInicio: '09:00',
  horarioFim: '09:30',
  status: 'AGENDADO',
  timetableId: null,
  observacao: null,
  origem: 'INTEGRACAO_EXTERNA',
  lastSyncedAt: null,
  syncStatus: 'SYNCED',
};

const julyAppointment = {
  ...existingAppointment,
  idLocal: 41,
  pacienteNome: 'Paciente Julho',
  data: '2026-07-10',
  horarioInicio: '09:00',
  horarioFim: '09:30',
};

function appointment(overrides: Partial<AgendaAgendamento> = {}): AgendaAgendamento {
  return {
    ...existingAppointment,
    ...overrides,
  };
}

const medwareProfissionais: AgendaProfissional[] = [
  { id: '101', nome: 'Médico Fictício A', origem: 'MEDWARE' },
  { id: '102', nome: 'Médico Fictício B', origem: 'MEDWARE' },
];

describe('AgendaClient', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-07-05T12:00:00-03:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('should_show_novo_agendamento_button_and_open_modal', () => {
    render(
      <AgendaClient
        initialAppointments={[]}
        initialProfissionais={profissionais}
        initialCapabilities={FULL_CAPABILITIES}
        initialError={null}
        weekStart="2026-06-23"
      />,
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /novo agendamento/i }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('should_hide_novo_agendamento_button_when_supportsWriteOperations_is_false', () => {
    render(
      <AgendaClient
        initialAppointments={[]}
        initialProfissionais={[]}
        initialCapabilities={NO_CATALOG_CAPABILITIES}
        initialError={null}
        weekStart="2026-06-23"
      />,
    );

    expect(screen.queryByRole('button', { name: /novo agendamento/i })).not.toBeInTheDocument();
  });

  it('should_show_encaixe_button_only_when_supportsFitIn_is_true', () => {
    const { unmount } = render(
      <AgendaClient
        initialAppointments={[]}
        initialProfissionais={profissionais}
        initialCapabilities={FULL_CAPABILITIES}
        initialError={null}
        weekStart="2026-06-23"
      />,
    );
    expect(screen.getByRole('button', { name: /^encaixe$/i })).toBeInTheDocument();
    unmount();

    render(
      <AgendaClient
        initialAppointments={[]}
        initialProfissionais={[]}
        initialCapabilities={NO_CATALOG_CAPABILITIES}
        initialError={null}
        weekStart="2026-06-23"
      />,
    );
    expect(screen.queryByRole('button', { name: /^encaixe$/i })).not.toBeInTheDocument();
  });

  it('should_hide_encaixe_button_when_catalog_is_supported_but_fitIn_is_not', () => {
    // Prova que Encaixe nunca e inferido de supportsCatalog: um provider hipotetico
    // com catalogo mas sem fitIn deve mostrar "Novo agendamento" e esconder "Encaixe".
    const catalogWithoutFitIn: AgendaCapabilities = {
      ...FULL_CAPABILITIES,
      supportsFitIn: false,
    };
    render(
      <AgendaClient
        initialAppointments={[]}
        initialProfissionais={profissionais}
        initialCapabilities={catalogWithoutFitIn}
        initialError={null}
        weekStart="2026-06-23"
      />,
    );

    expect(screen.getByRole('button', { name: /novo agendamento/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^encaixe$/i })).not.toBeInTheDocument();
  });

  it('should_not_change_capabilities_when_catalog_fetch_fails_transiently', async () => {
    const fetchMock = vi.fn((path: string) => {
      if (String(path).includes('/profissionais')) {
        return Promise.resolve({ ok: false, status: 501, json: async () => ({}) });
      }
      return Promise.resolve({ ok: true, json: async () => [] });
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <AgendaClient
        initialAppointments={[]}
        initialProfissionais={profissionais}
        initialCapabilities={FULL_CAPABILITIES}
        initialError={null}
        weekStart="2026-06-23"
      />,
    );

    expect(screen.getByRole('button', { name: /^encaixe$/i })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /mês atual/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    // Falha do catalogo (501) nao deve remover Encaixe/Novo agendamento — capacidades
    // vem exclusivamente do endpoint dedicado, nao da chamada de catalogo.
    expect(screen.getByRole('button', { name: /^encaixe$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /novo agendamento/i })).toBeInTheDocument();
  });

  it('should_display_appointment_from_api_in_week_grid', () => {
    render(
      <AgendaClient
        initialAppointments={[existingAppointment]}
        initialProfissionais={profissionais}
        initialCapabilities={FULL_CAPABILITIES}
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
        initialProfissionais={profissionais}
        initialCapabilities={FULL_CAPABILITIES}
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
        initialProfissionais={[]}
        initialCapabilities={FULL_CAPABILITIES}
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
        initialProfissionais={[]}
        initialCapabilities={FULL_CAPABILITIES}
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
        initialProfissionais={profissionais}
        initialCapabilities={FULL_CAPABILITIES}
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
      json: async () => (path.includes('/profissionais') ? profissionais : [julyAppointment]),
    }));
    vi.stubGlobal('fetch', fetchMock);

    render(
      <AgendaClient
        initialAppointments={[]}
        initialProfissionais={profissionais}
        initialCapabilities={FULL_CAPABILITIES}
        initialError={null}
        weekStart="2026-07-06"
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: /mês atual/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(decodeURIComponent(String(fetchMock.mock.calls[0][0]))).toContain('/api/agenda?startDate=');
    expect(decodeURIComponent(String(fetchMock.mock.calls[0][0]))).toContain('&endDate=');
    expect(await screen.findByText('Paciente Julho')).toBeInTheDocument();
    expect(screen.getByText('Agendamentos de 01/07/2026 a 31/07/2026')).toBeInTheDocument();
    expect(screen.getByText('Total no período')).toBeInTheDocument();
  });

  it('should_load_previous_month_period', async () => {
    const fetchMock = vi.fn((path: string) => Promise.resolve({
      ok: true,
      json: async () => (path.includes('/profissionais') ? profissionais : [existingAppointment]),
    }));
    vi.stubGlobal('fetch', fetchMock);

    render(
      <AgendaClient
        initialAppointments={[]}
        initialProfissionais={profissionais}
        initialCapabilities={FULL_CAPABILITIES}
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
      appointment({
        idLocal: 81, pacienteId: 91, pacienteNome: 'Paciente Novo', data: '2026-07-15', horarioInicio: '08:15', procedimentoNome: 'Procedimento A',
      }),
      appointment({
        idLocal: 82, pacienteId: 91, pacienteNome: 'Paciente Novo', data: '2026-07-15', horarioInicio: '08:15', procedimentoNome: 'Procedimento B',
      }),
    ];
    const fetchMock = vi.fn((path: string) => Promise.resolve({
      ok: true,
      json: async () => (path.includes('/profissionais') ? profissionais : nextAppointments),
    }));
    vi.stubGlobal('fetch', fetchMock);

    render(
      <AgendaClient
        initialAppointments={[appointment({
          idLocal: 80,
          pacienteNome: 'Paciente Anterior',
          data: '2026-07-13',
          horarioInicio: '09:00',
        })]}
        initialProfissionais={profissionais}
        initialCapabilities={FULL_CAPABILITIES}
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
    expect(appointmentsUrl).toContain('startDate=2026-07-13T00:00:00-03:00');
    expect(appointmentsUrl).toContain('endDate=2026-07-18T00:00:00-03:00');
    expect(screen.queryByText('Paciente Anterior')).not.toBeInTheDocument();
    expect(await screen.findAllByText('Paciente Novo')).toHaveLength(1);
    expect(screen.getByText('2 procedimentos')).toBeInTheDocument();
    expect(screen.getByText('Agendamentos de 13/07/2026 a 17/07/2026')).toBeInTheDocument();
  });

  it('should_keep_current_week_shortcut_and_switch_between_compact_days', async () => {
    const weekAppointments = [
      appointment({ idLocal: 83, pacienteNome: 'Paciente Segunda', data: '2026-07-13', horarioInicio: '08:15' }),
      appointment({ idLocal: 84, pacienteNome: 'Paciente Quarta', data: '2026-07-15', horarioInicio: '17:45' }),
    ];
    const fetchMock = vi.fn((path: string) => Promise.resolve({
      ok: true,
      json: async () => (path.includes('/profissionais') ? profissionais : weekAppointments),
    }));
    vi.stubGlobal('fetch', fetchMock);

    render(
      <AgendaClient
        initialAppointments={[]}
        initialProfissionais={profissionais}
        initialCapabilities={FULL_CAPABILITIES}
        initialError={null}
        weekStart="2026-07-13"
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: /semana atual/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    const appointmentsUrl = decodeURIComponent(String(fetchMock.mock.calls[0][0]));
    expect(appointmentsUrl).toContain('startDate=2026-07-13T00:00:00-03:00');
    expect(appointmentsUrl).toContain('endDate=2026-07-18T00:00:00-03:00');
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
      appointment({
        idLocal: 101, pacienteId: 201, pacienteNome: 'Paciente Fictício', profissionalId: '101', profissionalNome: 'Médico Fictício A', data: '2026-07-15', horarioInicio: '08:15', procedimentoNome: 'Procedimento Delta',
      }),
      appointment({
        idLocal: 102, pacienteId: 201, pacienteNome: 'Paciente Fictício', profissionalId: '101', profissionalNome: 'Médico Fictício A', data: '2026-07-15', horarioInicio: '08:15', procedimentoNome: 'Procedimento Alfa',
      }),
      appointment({
        idLocal: 103, pacienteId: 201, pacienteNome: 'Paciente Fictício', profissionalId: '101', profissionalNome: 'Médico Fictício A', data: '2026-07-15', horarioInicio: '08:15', procedimentoNome: 'Procedimento Gama',
      }),
      appointment({
        idLocal: 104, pacienteId: 201, pacienteNome: 'Paciente Fictício', profissionalId: '101', profissionalNome: 'Médico Fictício A', data: '2026-07-15', horarioInicio: '08:15', procedimentoNome: 'Procedimento Beta',
      }),
    ];

    render(
      <AgendaClient
        initialAppointments={appointments}
        initialProfissionais={medwareProfissionais}
        initialCapabilities={FULL_CAPABILITIES}
        initialError={null}
        weekStart="2026-07-13"
      />,
    );

    expect(screen.getAllByText('Paciente Fictício')).toHaveLength(1);
    expect(screen.getByText('4 procedimentos')).toBeInTheDocument();
    const groupButton = screen.getByLabelText(/Paciente Fictício/);
    expect(groupButton).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(groupButton);
    expect(screen.getAllByText('Procedimento Alfa').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Procedimento Beta').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Procedimento Gama').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Procedimento Delta').length).toBeGreaterThan(0);
    expect(groupButton).toHaveAttribute('aria-expanded', 'true');
    expect(groupButton).toHaveAttribute(
      'data-appointment-ids',
      '102,104,101,103',
    );
    fireEvent.click(groupButton);
    expect(groupButton).toHaveAttribute('aria-expanded', 'false');
  });

  it('should_keep_groups_separate_by_doctor_status_and_patient', () => {
    const base = {
      data: '2026-07-15',
      horarioInicio: '08:15',
    };
    const appointments = [
      appointment({
        ...base, idLocal: 201, pacienteId: 301, pacienteNome: 'Paciente Um', profissionalNome: 'Médico Fictício A', profissionalId: '101', status: 'AGENDADO', procedimentoNome: 'Procedimento A',
      }),
      appointment({
        ...base, idLocal: 202, pacienteId: 301, pacienteNome: 'Paciente Um', profissionalNome: 'Médico Fictício B', profissionalId: '102', status: 'AGENDADO', procedimentoNome: 'Procedimento B',
      }),
      appointment({
        ...base, idLocal: 203, pacienteId: 301, pacienteNome: 'Paciente Um', profissionalNome: 'Médico Fictício A', profissionalId: '101', status: 'CANCELADO', procedimentoNome: 'Procedimento C',
      }),
      appointment({
        ...base, idLocal: 204, pacienteId: 302, pacienteNome: 'Paciente Dois', profissionalNome: 'Médico Fictício A', profissionalId: '101', status: 'AGENDADO', procedimentoNome: 'Procedimento D',
      }),
    ];

    const groups = groupAppointmentsForAgenda(appointments);

    expect(groups).toHaveLength(4);
    expect(groups.map((group) => group.appointmentIds)).toEqual([[204], [201], [202], [203]]);
  });

  it('should_render_single_procedure_without_multiple_procedures_label', () => {
    render(
      <AgendaClient
        initialAppointments={[appointment({
          idLocal: 301,
          data: '2026-07-15',
          horarioInicio: '17:45',
          procedimentoNome: 'Procedimento Único',
        })]}
        initialProfissionais={profissionais}
        initialCapabilities={FULL_CAPABILITIES}
        initialError={null}
        weekStart="2026-07-13"
      />,
    );

    expect(screen.getAllByText('Procedimento Único').length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText(/procedimentos$/i)).not.toBeInTheDocument();
    expect(screen.getByText(/17:45/)).toBeInTheDocument();
  });

  it('should_filter_groups_by_medware_doctor', () => {
    const appointments = [
      appointment({
        idLocal: 401, pacienteNome: 'Paciente Médico A', profissionalId: '101', profissionalNome: 'Médico Fictício A', data: '2026-07-15', horarioInicio: '08:15', procedimentoNome: 'Procedimento A',
      }),
      appointment({
        idLocal: 402, pacienteNome: 'Paciente Médico B', profissionalId: '102', profissionalNome: 'Médico Fictício B', data: '2026-07-15', horarioInicio: '08:15', procedimentoNome: 'Procedimento B',
      }),
    ];
    render(
      <AgendaClient
        initialAppointments={appointments}
        initialProfissionais={medwareProfissionais}
        initialCapabilities={FULL_CAPABILITIES}
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
      appointment({
        idLocal: 451, pacienteNome: 'Ana Lara Lopes Ferreira', profissionalId: '30', profissionalNome: 'Dra. Renata', data: '2026-07-15', horarioInicio: '08:15',
      }),
      appointment({
        idLocal: 452, pacienteNome: 'João Souza', profissionalId: '31', profissionalNome: 'Dr. Paulo', data: '2026-07-15', horarioInicio: '09:00',
      }),
    ];
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(
      <AgendaClient
        initialAppointments={appointments}
        initialProfissionais={[...profissionais, { id: '31', nome: 'Dr. Paulo', origem: 'CRM' }]}
        initialCapabilities={FULL_CAPABILITIES}
        initialError={null}
        weekStart="2026-07-13"
      />,
    );

    const search = screen.getByRole('searchbox', { name: 'Buscar paciente na agenda' });
    fireEvent.change(search, { target: { value: '  ferreira   ana ' } });
    expect(screen.getByText('Ana Lara Lopes Ferreira')).toBeInTheDocument();
    expect(screen.queryByText('João Souza')).not.toBeInTheDocument();
    expect(screen.getByText('1 agendamentos encontrados')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();

    fireEvent.keyDown(search, { key: 'Escape' });
    expect(screen.getByText('João Souza')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Dra. Renata' }));
    fireEvent.change(search, { target: { value: 'joao' } });
    expect(screen.getByText('Nenhum agendamento encontrado para este paciente no período selecionado.')).toBeInTheDocument();
  });

  it('should_show_start_time_and_empty_selected_day', () => {
    render(
      <AgendaClient
        initialAppointments={[
          appointment({
            idLocal: 501, data: '2026-07-15', horarioInicio: '08:15', procedimentoNome: 'Procedimento Manhã',
          }),
          appointment({
            idLocal: 502, data: '2026-07-15', horarioInicio: '17:45', procedimentoNome: 'Procedimento Tarde',
          }),
        ]}
        initialProfissionais={profissionais}
        initialCapabilities={FULL_CAPABILITIES}
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
      idLocal: 600 + index,
      pacienteId: 700 + Math.floor(index / 2),
      pacienteNome: `Paciente Fictício ${Math.floor(index / 2)}`,
      profissionalId: '101',
      profissionalNome: 'Médico Fictício A',
      data: '2026-07-15',
      horarioInicio: '08:15',
      procedimentoNome: `Procedimento ${index % 2 === 0 ? 'A' : 'B'}`,
    }));

    const groups = groupAppointmentsForAgenda(appointments);
    const groupedIds = groups.flatMap((group) => group.appointmentIds);

    expect(groups.length).toBeLessThan(appointments.length);
    expect(groupedIds).toHaveLength(62);
    expect(new Set(groupedIds).size).toBe(62);
    expect(groupedIds.sort((left, right) => left - right)).toEqual(
      appointments.map((item) => item.idLocal),
    );

    render(
      <AgendaClient
        initialAppointments={appointments}
        initialProfissionais={medwareProfissionais}
        initialCapabilities={FULL_CAPABILITIES}
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
          appointment({ idLocal: 701, pacienteNome: 'Paciente Segunda', data: '2026-07-13', horarioInicio: '08:15' }),
          appointment({ idLocal: 702, pacienteNome: 'Paciente Hoje', data: '2026-07-15', horarioInicio: '09:30' }),
        ]}
        initialProfissionais={profissionais}
        initialCapabilities={FULL_CAPABILITIES}
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
        idLocal: 801,
        profissionalId: '1',
        profissionalNome: 'Medico A',
        data: '2026-07-15',
        horarioInicio: '08:15',
        procedimentoNome: 'Ultrassonografia',
      }),
      appointment({
        idLocal: 802,
        profissionalId: '1',
        profissionalNome: 'Medico A',
        data: '2026-07-15',
        horarioInicio: '09:15',
        procedimentoNome: 'Ultrassonografia',
      }),
      appointment({
        idLocal: 803,
        profissionalId: '2',
        profissionalNome: 'Medico B',
        data: '2026-07-15',
        horarioInicio: '10:15',
        procedimentoNome: 'Consulta ginecologica',
      }),
    ];

    render(
      <AgendaClient
        initialAppointments={appointments}
        initialProfissionais={[
          { id: '1', nome: 'Medico A', origem: 'CRM' },
          { id: '2', nome: 'Medico B', origem: 'CRM' },
        ]}
        initialCapabilities={FULL_CAPABILITIES}
        initialError={null}
        weekStart="2026-07-13"
      />,
    );

    expect(screen.getByTitle('Medico A · Ultrassonografia: 2')).toBeInTheDocument();
    expect(screen.getByTitle('Medico B · Ultrassonografia: 0')).toBeInTheDocument();
    expect(screen.getByTitle('Medico A · Consulta ginecologica: 0')).toBeInTheDocument();
    expect(screen.getByTitle('Medico B · Consulta ginecologica: 1')).toBeInTheDocument();
  });
});
