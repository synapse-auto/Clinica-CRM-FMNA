import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  adicionarTagPaciente,
  removerTagPaciente,
} from './pacientes';

describe('pacientes service', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('should_link_and_unlink_tags_through_paciente_bff', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([{ id: 9, nome: 'Prioridade', cor: '#ef4444', ativo: true }], 201))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await adicionarTagPaciente(12, 9);
    await removerTagPaciente(12, 9);

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/pacientes/12/tags/9',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/pacientes/12/tags/9',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });
});

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
