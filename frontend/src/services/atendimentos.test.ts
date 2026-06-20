import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  enviarAnexo,
  enviarMensagem,
  listAtendimentos,
  revisarConvenio,
} from './atendimentos';

describe('atendimentos service', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('should_send_real_filters_to_bff_without_mock_fallback', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
    }));
    vi.stubGlobal('fetch', fetchMock);

    await listAtendimentos({
      filtro: 'NAO_LIDOS',
      tipo: 'HUMANO',
      busca: 'Paciente',
    });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/api/atendimentos?'),
      expect.objectContaining({ cache: 'no-store' }),
    );
    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toContain('filtro=NAO_LIDOS');
    expect(url).toContain('tipo=HUMANO');
    expect(url).toContain('busca=Paciente');
  });

  it('should_send_text_and_surface_backend_failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(
      { message: 'WhatsApp/Meta não configurado' },
      409,
    )));

    await expect(enviarMensagem(12, 'Olá')).rejects.toThrow(
      'WhatsApp/Meta não configurado',
    );
  });

  it('should_upload_attachment_as_form_data', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1 }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const file = new File(['pdf'], 'guia.pdf', { type: 'application/pdf' });

    await enviarAnexo(12, file);

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.method).toBe('POST');
    expect(init.body).toBeInstanceOf(FormData);
  });

  it('should_persist_convenio_review_through_bff', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 12 }));
    vi.stubGlobal('fetch', fetchMock);

    await revisarConvenio(12, 'APROVADO');

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/atendimentos/12/convenio',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ resultado: 'APROVADO' }),
      }),
    );
  });
});

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
