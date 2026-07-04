import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  adicionarTagAtendimento,
  ativarIaAtendimento,
  enviarAnexo,
  enviarMensagem,
  getMensagensRapidasAtivas,
  getTagsOperacionaisAtivas,
  listAtendimentos,
  removerTagAtendimento,
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

  it('should_load_only_active_quick_messages_for_chat', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([
      { id: 2, titulo: 'Inativa', atalho: '/b', conteudo: 'B', ativo: false },
      { id: 1, titulo: 'Confirmar', atalho: '/a', conteudo: 'A', ativo: true },
    ])));

    const result = await getMensagensRapidasAtivas();

    expect(result).toHaveLength(1);
    expect(result[0].titulo).toBe('Confirmar');
  });

  it('should_load_only_active_tags_for_atendimento_picker', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([
      { id: 1, nome: 'Zeta', cor: '#64748b', ativo: true },
      { id: 2, nome: 'Inativa', cor: '#ef4444', ativo: false },
      { id: 3, nome: 'Alfa', cor: '#0d9488', ativo: true },
    ])));

    const result = await getTagsOperacionaisAtivas();

    expect(result.map((tag) => tag.nome)).toEqual(['Alfa', 'Zeta']);
  });

  it('should_link_and_unlink_tags_through_atendimento_bff', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([{ id: 9, nome: 'Prioridade', cor: '#ef4444', ativo: true }], 201))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await adicionarTagAtendimento(12, 9);
    await removerTagAtendimento(12, 9);

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/atendimentos/12/tags/9',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/atendimentos/12/tags/9',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });

  it('should_return_atendimento_to_ai_mode_through_bff', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 12, tratadoPorIa: true }));
    vi.stubGlobal('fetch', fetchMock);

    await ativarIaAtendimento(12);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/atendimentos/12/modo-ia',
      expect.objectContaining({ method: 'PATCH' }),
    );
  });
});

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
