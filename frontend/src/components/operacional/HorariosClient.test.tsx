import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { HorariosClient } from './HorariosClient';
import type { HorarioAtendente } from '@/types/operacional';
import type { AtendenteOption } from '@/types/atendimento';

const attendants: AtendenteOption[] = [
  { id: 3, nome: 'Recepcao Real', perfil: 'RECEPCIONISTA' },
];

const schedules: HorarioAtendente[] = [
  {
    id: 9,
    usuarioId: 3,
    usuarioNome: 'Recepcao Real',
    diaSemana: 1,
    horaInicio: '08:00:00',
    horaFim: '12:00:00',
    ativo: true,
    criadoEm: '2026-07-01T12:00:00Z',
    atualizadoEm: '2026-07-01T12:00:00Z',
  },
];

describe('HorariosClient', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('should_render_real_schedules_without_fake_week_grid', () => {
    render(<HorariosClient initialSchedules={schedules} attendants={attendants} initialError={null} canManage />);

    expect(screen.getByText('Recepcao Real')).toBeInTheDocument();
    expect(screen.getByText('08:00 - 12:00')).toBeInTheDocument();
    expect(screen.queryByText('24 horas')).not.toBeInTheDocument();
  });

  it('should_create_schedule_using_bff', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      ...schedules[0],
      id: 12,
      diaSemana: 2,
      horaInicio: '13:00:00',
      horaFim: '18:00:00',
    }), {
      status: 201,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);
    render(<HorariosClient initialSchedules={[]} attendants={attendants} initialError={null} canManage />);

    await user.click(screen.getByRole('button', { name: /novo horário/i }));
    await user.click(screen.getByLabelText('Atendente'));
    await user.click(await screen.findByRole('option', { name: 'Recepcao Real' }));
    await user.click(screen.getByLabelText('Dia da semana'));
    await user.click(await screen.findByRole('option', { name: 'Terça-feira' }));
    await user.clear(screen.getByLabelText('Hora início'));
    await user.type(screen.getByLabelText('Hora início'), '13:00');
    await user.clear(screen.getByLabelText('Hora fim'));
    await user.type(screen.getByLabelText('Hora fim'), '18:00');
    await user.click(screen.getByRole('button', { name: 'Salvar horário' }));

    await waitFor(() => expect(screen.getByText('13:00 - 18:00')).toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledWith('/api/horarios', expect.objectContaining({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        usuarioId: 3,
        diaSemana: 2,
        horaInicio: '13:00',
        horaFim: '18:00',
        ativo: true,
      }),
    }));
  });
});
