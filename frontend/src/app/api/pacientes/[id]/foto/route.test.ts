import { beforeEach, describe, expect, it, vi } from 'vitest';

const forwardBackendRequestMock = vi.hoisted(() => vi.fn());

vi.mock('@/services/backend', () => ({
  forwardBackendRequest: forwardBackendRequestMock,
}));

import { GET } from './route';

describe('foto do paciente BFF route', () => {
  beforeEach(() => vi.clearAllMocks());

  it('forwards_etag_to_the_authenticated_backend_route', async () => {
    forwardBackendRequestMock.mockResolvedValue(new Response(new Uint8Array([1, 2, 3]), {
      status: 200,
      headers: { 'Content-Type': 'image/jpeg' },
    }));
    const request = new Request('http://localhost/api/pacientes/42/foto', {
      headers: { 'If-None-Match': '"hash-ficticio"' },
    });

    const response = await GET(request, { params: Promise.resolve({ id: '42' }) });

    expect(response.status).toBe(200);
    expect(forwardBackendRequestMock).toHaveBeenCalledWith('/api/pacientes/42/foto', {
      headers: {
        Accept: 'image/jpeg,image/png,image/webp',
        'If-None-Match': '"hash-ficticio"',
      },
    });
  });
});
