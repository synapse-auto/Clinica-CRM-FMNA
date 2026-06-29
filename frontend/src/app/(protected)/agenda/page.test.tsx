import { describe, expect, it, vi } from 'vitest';

const redirectMock = vi.hoisted(() => vi.fn((path: string) => {
  throw new Error(`redirect:${path}`);
}));

const getAgendamentosMock = vi.hoisted(() => vi.fn());
const getAgendaOptionsMock = vi.hoisted(() => vi.fn());
const isBackendAuthorizationErrorMock = vi.hoisted(() => vi.fn());

vi.mock('next/navigation', () => ({
  redirect: redirectMock,
}));

vi.mock('@/services/backend', () => ({
  getAgendamentos: getAgendamentosMock,
  getAgendaOptions: getAgendaOptionsMock,
  isBackendAuthorizationError: isBackendAuthorizationErrorMock,
}));

import AgendaPage from './page';

describe('AgendaPage', () => {
  it('should_redirect_to_login_when_backend_rejects_session', async () => {
    const authError = new Error('sessao invalida');
    getAgendamentosMock.mockRejectedValue(authError);
    getAgendaOptionsMock.mockResolvedValue({ pacientes: [], medicos: [] });
    isBackendAuthorizationErrorMock.mockReturnValue(true);

    await expect(AgendaPage()).rejects.toThrow('redirect:/login');
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });
});
