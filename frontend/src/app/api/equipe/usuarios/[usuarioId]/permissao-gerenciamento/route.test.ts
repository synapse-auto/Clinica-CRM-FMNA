import { beforeEach, describe, expect, it, vi } from 'vitest';

const forwardBackendRequestMock = vi.hoisted(() => vi.fn());

vi.mock('@/services/backend', () => ({
  forwardBackendRequest: forwardBackendRequestMock,
}));

import { PATCH } from './route';

describe('permissao-gerenciamento BFF route', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_forward_only_the_permission_payload_to_current_backend_session', async () => {
    forwardBackendRequestMock.mockResolvedValue(Response.json({
      id: 15,
      podeGerenciarUsuarios: true,
    }));
    const request = new Request('http://localhost/api/equipe/usuarios/15/permissao-gerenciamento', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ podeGerenciarUsuarios: true }),
    });

    const response = await PATCH(request, { params: Promise.resolve({ usuarioId: '15' }) });

    expect(response.status).toBe(200);
    expect(forwardBackendRequestMock).toHaveBeenCalledWith(
      '/api/equipe/usuarios/15/permissao-gerenciamento',
      {
        method: 'PATCH',
        body: JSON.stringify({ podeGerenciarUsuarios: true }),
        headers: { 'Content-Type': 'application/json' },
      },
    );
  });
});
