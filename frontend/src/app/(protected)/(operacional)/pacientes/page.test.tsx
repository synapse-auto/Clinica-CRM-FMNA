import { describe, expect, it, vi } from 'vitest';

const redirectMock = vi.hoisted(() => vi.fn((path: string) => {
  throw new Error(`redirect:${path}`);
}));

const getPacientesMock = vi.hoisted(() => vi.fn());
const getTagsMock = vi.hoisted(() => vi.fn());
const isBackendAuthorizationErrorMock = vi.hoisted(() => vi.fn());
const requireSessionMock = vi.hoisted(() => vi.fn());

vi.mock('next/navigation', () => ({
  redirect: redirectMock,
}));

vi.mock('@/services/backend', () => ({
  getPacientes: getPacientesMock,
  getTags: getTagsMock,
  isBackendAuthorizationError: isBackendAuthorizationErrorMock,
}));

vi.mock('@/lib/auth/session', () => ({
  requireSession: requireSessionMock,
}));

import PacientesPage from './page';

describe('PacientesPage', () => {
  it('should_redirect_to_login_when_backend_rejects_session', async () => {
    const authError = new Error('sessao invalida');
    requireSessionMock.mockResolvedValue({ perfil: 'GESTOR' });
    getPacientesMock.mockRejectedValue(authError);
    getTagsMock.mockResolvedValue([]);
    isBackendAuthorizationErrorMock.mockReturnValue(true);

    await expect(PacientesPage()).rejects.toThrow('redirect:/login');
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });
});
