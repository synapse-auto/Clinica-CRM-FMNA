import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  adicionarTagPaciente,
  confirmarImportacaoCsv,
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

  it('should_send_csv_confirmation_as_multipart_with_hash_and_mapping', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ created: 1 }));
    vi.stubGlobal('fetch', fetchMock);

    await confirmarImportacaoCsv(
      new File(['nome;telefone\nAna;5583999999999'], 'contatos.csv', { type: 'text/csv' }),
      'a'.repeat(64),
      { nameColumn: 'nome', phoneColumn: 'telefone' },
    );

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/pacientes/importacoes/csv',
      expect.objectContaining({ method: 'POST', body: expect.any(FormData) }),
    );
    const form = fetchMock.mock.calls[0][1].body as FormData;
    expect(form.get('expectedFileHash')).toBe('a'.repeat(64));
    expect(form.get('file')).toBeInstanceOf(File);
  });
});

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
