import { describe, expect, it, vi } from 'vitest';

const redirectMock = vi.hoisted(() => vi.fn((path: string) => {
  throw new Error(`redirect:${path}`);
}));
const requireSessionMock = vi.hoisted(() => vi.fn());
const getEquipeMock = vi.hoisted(() => vi.fn());
const isBackendAuthorizationErrorMock = vi.hoisted(() => vi.fn());

vi.mock('next/navigation', () => ({ redirect: redirectMock }));
vi.mock('@/lib/auth/session', () => ({ requireSession: requireSessionMock }));
vi.mock('@/services/backend', () => ({
  getEquipe: getEquipeMock,
  isBackendAuthorizationError: isBackendAuthorizationErrorMock,
}));
vi.mock('@/components/equipe/EquipeClient', () => ({
  EquipeClient: () => null,
}));

import EquipePage from './page';

describe('EquipePage', () => {
  it('should_redirect_manager_without_permission', async () => {
    requireSessionMock.mockResolvedValue({
      perfil: 'GESTOR',
      podeGerenciarUsuarios: false,
    });

    await expect(EquipePage()).rejects.toThrow('redirect:/configuracoes');
    expect(getEquipeMock).not.toHaveBeenCalled();
  });

  it('should_allow_newly_authorized_manager_after_session_refresh', async () => {
    requireSessionMock.mockResolvedValue({
      perfil: 'GESTOR',
      podeGerenciarUsuarios: true,
    });
    getEquipeMock.mockResolvedValue({ grupos: [] });

    await expect(EquipePage()).resolves.toBeTruthy();
    expect(requireSessionMock).toHaveBeenCalledWith(['GESTOR']);
    expect(getEquipeMock).toHaveBeenCalledTimes(1);
  });
});
