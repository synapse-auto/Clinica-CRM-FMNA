import { afterEach, describe, expect, it, vi } from 'vitest';
import { createAgendamento } from './agendamentos';

describe('agendamentos service', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('should_post_appointment_to_next_route_handler', async () => {
    const payload = {
      pacienteId: 20,
      medicoId: 30,
      dataHoraInicio: '2026-06-22T09:00:00-03:00',
      dataHoraFim: '2026-06-22T09:30:00-03:00',
      tipo: 'CONSULTA',
      servicoNome: 'Pré-natal',
    };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 40 }), {
      status: 201,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);

    await createAgendamento(payload);

    expect(fetchMock).toHaveBeenCalledWith('/api/agendamentos', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(payload),
    }));
  });

  it('should_throw_backend_message_when_request_fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ message: 'Paciente não encontrado' }),
      {
        status: 404,
        headers: { 'Content-Type': 'application/json' },
      },
    )));

    await expect(createAgendamento({
      pacienteId: 999,
      medicoId: null,
      dataHoraInicio: '2026-06-22T09:00:00-03:00',
      dataHoraFim: null,
      tipo: 'CONSULTA',
      servicoNome: 'Consulta',
    })).rejects.toThrow('Paciente não encontrado');
  });
});
