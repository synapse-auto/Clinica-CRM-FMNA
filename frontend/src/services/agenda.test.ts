import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  atualizarAgendamento,
  buscarAgendamento,
  cancelarAgendamento,
  criarAgendamento,
  criarEncaixe,
  criarOuLocalizarPaciente,
  listarAgenda,
  listarConvenios,
  listarHorariosDisponiveis,
  listarLocais,
  listarPacientesMedware,
  listarPorPaciente,
  listarProcedimentos,
  listarProfissionais,
} from './agenda';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('agenda service', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('should_list_agenda_with_start_and_end_date_params', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal('fetch', fetchMock);

    await listarAgenda('2026-07-13T00:00:00-03:00', '2026-07-18T00:00:00-03:00');

    const [url] = fetchMock.mock.calls[0];
    expect(decodeURIComponent(String(url))).toBe(
      '/api/agenda?startDate=2026-07-13T00:00:00-03:00&endDate=2026-07-18T00:00:00-03:00',
    );
  });

  it('should_fetch_single_appointment_by_id', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ idLocal: 5 }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await buscarAgendamento(5);

    expect(fetchMock).toHaveBeenCalledWith('/api/agenda/5', expect.objectContaining({}));
    expect(result).toEqual({ idLocal: 5 });
  });

  it('should_list_appointments_by_patient_cpf', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal('fetch', fetchMock);

    await listarPorPaciente('11144477735');

    expect(fetchMock).toHaveBeenCalledWith('/api/agenda/paciente?cpf=11144477735', expect.objectContaining({}));
  });

  it('should_list_professionals', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([{ id: '1', nome: 'Dra. Ana', origem: 'CRM' }]));
    vi.stubGlobal('fetch', fetchMock);

    const result = await listarProfissionais();

    expect(fetchMock).toHaveBeenCalledWith('/api/agenda/profissionais', expect.objectContaining({}));
    expect(result).toEqual([{ id: '1', nome: 'Dra. Ana', origem: 'CRM' }]);
  });

  it('should_list_available_slots_with_repeated_professionalId_params', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal('fetch', fetchMock);

    await listarHorariosDisponiveis('2026-07-20', ['10', '20']);

    const [url] = fetchMock.mock.calls[0];
    expect(decodeURIComponent(String(url))).toBe(
      '/api/agenda/horarios?date=2026-07-20&professionalId=10&professionalId=20',
    );
  });

  it('should_list_procedures_and_convenios_scoped_by_local', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]));
    vi.stubGlobal('fetch', fetchMock);

    await listarProcedimentos('loc-1');
    await listarConvenios('loc-1');

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/agenda/procedimentos?localId=loc-1', expect.objectContaining({}));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/agenda/convenios?localId=loc-1', expect.objectContaining({}));
  });

  it('should_list_locais_without_query_params', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal('fetch', fetchMock);

    await listarLocais();

    expect(fetchMock).toHaveBeenCalledWith('/api/agenda/locais', expect.objectContaining({}));
  });

  it('should_create_or_locate_patient_by_cpf', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1, nome: 'Maria', cpfMascarado: '***' }));
    vi.stubGlobal('fetch', fetchMock);

    const payload = { cpf: '11144477735', nome: 'Maria', telefone: null, email: null, dataNascimento: null };
    const result = await criarOuLocalizarPaciente(payload);

    expect(fetchMock).toHaveBeenCalledWith('/api/agenda/pacientes', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(payload),
    }));
    expect(result).toEqual({ id: 1, nome: 'Maria', cpfMascarado: '***' });
  });

  it('should_create_appointment_via_post', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ idLocal: 1 }, 201));
    vi.stubGlobal('fetch', fetchMock);

    await criarAgendamento({
      pacienteId: 1,
      pacienteCpf: null,
      profissionalId: '10',
      localId: 'loc-1',
      timetableId: 'tt-1',
      data: '2026-07-20',
      horarioInicio: '09:00',
      horarioFim: '09:30',
      procedimentoId: null,
      procedimentoNome: null,
      convenioId: null,
      observacao: null,
    });

    expect(fetchMock).toHaveBeenCalledWith('/api/agenda', expect.objectContaining({ method: 'POST' }));
  });

  it('should_create_fitin_via_encaixe_endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ idLocal: 1 }, 201));
    vi.stubGlobal('fetch', fetchMock);

    await criarEncaixe({
      pacienteId: 1,
      pacienteCpf: null,
      profissionalId: '10',
      localId: 'loc-1',
      timetableId: null,
      data: '2026-07-20',
      horarioInicio: '09:00',
      horarioFim: '09:30',
      procedimentoId: null,
      procedimentoNome: null,
      convenioId: null,
      observacao: null,
    });

    expect(fetchMock).toHaveBeenCalledWith('/api/agenda/encaixe', expect.objectContaining({ method: 'POST' }));
  });

  it('should_update_appointment_via_put', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ idLocal: 1 }));
    vi.stubGlobal('fetch', fetchMock);

    await atualizarAgendamento(1, {
      status: null,
      data: '2026-07-21',
      horarioInicio: '10:00',
      horarioFim: '10:30',
      timetableId: null,
      procedimentoId: null,
      convenioId: null,
      observacao: null,
    });

    expect(fetchMock).toHaveBeenCalledWith('/api/agenda/1', expect.objectContaining({ method: 'PUT' }));
  });

  it('should_cancel_appointment_with_reason_as_query_param', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await cancelarAgendamento(1, 'Paciente remarcou');

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/agenda/1?motivo=Paciente+remarcou',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });

  it('should_throw_backend_message_when_cancel_fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ message: 'Já cancelado' }, 409)));

    await expect(cancelarAgendamento(1, 'motivo')).rejects.toThrow('Já cancelado');
  });

  it('should_list_medware_patients_from_existing_pacientes_endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([{ id: 1, nome: 'Maria' }]));
    vi.stubGlobal('fetch', fetchMock);

    const result = await listarPacientesMedware();

    expect(fetchMock).toHaveBeenCalledWith('/api/pacientes', expect.objectContaining({}));
    expect(result).toEqual([{ id: 1, nome: 'Maria' }]);
  });

  it('should_throw_generic_message_when_error_body_is_not_json', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('erro interno', { status: 500 })));

    await expect(listarAgenda('2026-07-13T00:00:00-03:00', '2026-07-18T00:00:00-03:00')).rejects.toThrow(
      'Não foi possível concluir a operação (500)',
    );
  });
});
