import { beforeEach, describe, expect, it, vi } from 'vitest';

const forwardBackendRequestMock = vi.hoisted(() => vi.fn());

vi.mock('@/services/backend', () => ({
  forwardBackendRequest: forwardBackendRequestMock,
}));

import { PATCH } from './route';

describe('nome de usuário BFF route', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should_forward_only_the_name_to_the_authenticated_backend_session', async () => {
    forwardBackendRequestMock.mockResolvedValue(Response.json({ id: 15, nome: 'Nome Atualizado' }));
    const request = new Request('http://localhost/api/equipe/usuarios/15/nome', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nome: 'Nome Atualizado' }),
    });

    const response = await PATCH(request, { params: Promise.resolve({ usuarioId: '15' }) });

    expect(response.status).toBe(200);
    expect(forwardBackendRequestMock).toHaveBeenCalledWith('/api/equipe/usuarios/15/nome', {
      method: 'PATCH',
      body: JSON.stringify({ nome: 'Nome Atualizado' }),
      headers: { 'Content-Type': 'application/json' },
    });
  });
});
