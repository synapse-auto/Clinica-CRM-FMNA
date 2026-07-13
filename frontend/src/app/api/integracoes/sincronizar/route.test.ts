import { beforeEach, describe, expect, it, vi } from 'vitest';

const forwardBackendRequestMock = vi.hoisted(() => vi.fn());

vi.mock('@/services/backend', () => ({
  forwardBackendRequest: forwardBackendRequestMock,
}));

import { POST } from './route';

const VALID_URL = 'http://localhost/api/integracoes/sincronizar'
  + '?dataInicio=01%2F06%2F2026&dataFim=31%2F07%2F2026';

describe('integracoes sincronizar BFF route', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    forwardBackendRequestMock.mockResolvedValue(Response.json({ status: 'SUCESSO' }));
  });

  it('should_forward_one_post_to_the_expected_backend_path_when_period_is_valid', async () => {
    const request = postRequest(VALID_URL);
    expect(request.body).toBeNull();

    const response = await POST(request);

    expect(response.status).toBe(200);
    expect(forwardBackendRequestMock).toHaveBeenCalledTimes(1);
    expect(forwardBackendRequestMock).toHaveBeenCalledWith(
      '/api/integracoes/sincronizar?dataInicio=01%2F06%2F2026&dataFim=31%2F07%2F2026',
      { method: 'POST' },
    );
  });

  it('should_accept_a_non_null_empty_stream_like_a_real_browser_request', async () => {
    const request = emptyStreamRequest(VALID_URL);
    expect(request.body).not.toBeNull();

    const response = await POST(request);

    expect(response.status).toBe(200);
    expect(forwardBackendRequestMock).toHaveBeenCalledTimes(1);
    expect(forwardBackendRequestMock).toHaveBeenCalledWith(
      '/api/integracoes/sincronizar?dataInicio=01%2F06%2F2026&dataFim=31%2F07%2F2026',
      { method: 'POST' },
    );
  });

  it('should_accept_an_empty_uint8array_body', async () => {
    const request = new Request(VALID_URL, {
      method: 'POST',
      body: new Uint8Array(0),
    });
    expect(request.body).not.toBeNull();

    const response = await POST(request);

    expect(response.status).toBe(200);
    expect(forwardBackendRequestMock).toHaveBeenCalledTimes(1);
  });

  it('should_accept_an_empty_text_body', async () => {
    const request = new Request(VALID_URL, { method: 'POST', body: '' });
    expect(request.body).not.toBeNull();

    const response = await POST(request);

    expect(response.status).toBe(200);
    expect(forwardBackendRequestMock).toHaveBeenCalledTimes(1);
  });

  it('should_validate_dates_after_accepting_an_empty_stream', async () => {
    const response = await POST(emptyStreamRequest(
      'http://localhost/api/integracoes/sincronizar'
      + '?dataInicio=2026-06-01&dataFim=2026-07-31',
    ));

    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({
      message: 'Informe um período válido no formato dd/MM/yyyy.',
    });
    expect(forwardBackendRequestMock).not.toHaveBeenCalled();
  });

  it('should_not_forward_unexpected_query_parameters', async () => {
    const response = await POST(postRequest(`${VALID_URL}&clinicaId=99`));

    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({
      message: 'Apenas dataInicio e dataFim são permitidos.',
    });
    expect(forwardBackendRequestMock).not.toHaveBeenCalled();
  });

  it('should_not_accept_duplicate_period_parameters', async () => {
    const response = await POST(postRequest(
      `${VALID_URL}&dataInicio=02%2F06%2F2026`,
    ));

    expect(response.status).toBe(400);
    expect(forwardBackendRequestMock).not.toHaveBeenCalled();
  });

  it('should_not_accept_or_forward_a_request_body', async () => {
    const response = await POST(new Request(VALID_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ unexpected: 'value' }),
    }));

    expect(response.status).toBe(400);
    expect(forwardBackendRequestMock).not.toHaveBeenCalled();
  });

  it.each([
    ['um espaço', ' '],
    ['objeto JSON vazio', '{}'],
    ['JSON real', JSON.stringify({ unexpected: 'value' })],
  ])('should_reject_a_body_containing_%s', async (_scenario, body) => {
    const response = await POST(new Request(VALID_URL, {
      method: 'POST',
      body,
    }));

    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({
      message: 'Esta operação não aceita corpo de requisição.',
    });
    expect(forwardBackendRequestMock).not.toHaveBeenCalled();
  });

  it('should_return_a_sanitized_error_when_the_body_cannot_be_read', async () => {
    const request = postRequest(VALID_URL);
    vi.spyOn(request, 'arrayBuffer').mockRejectedValue(new Error('stream failure'));

    const response = await POST(request);

    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({
      message: 'Não foi possível validar a requisição.',
    });
    expect(forwardBackendRequestMock).not.toHaveBeenCalled();
  });

  it.each([
    ['dataInicio ausente', 'http://localhost/api/integracoes/sincronizar?dataFim=31%2F07%2F2026'],
    ['dataFim ausente', 'http://localhost/api/integracoes/sincronizar?dataInicio=01%2F06%2F2026'],
    ['formato ISO', 'http://localhost/api/integracoes/sincronizar?dataInicio=2026-06-01&dataFim=31%2F07%2F2026'],
    ['data inicial impossível', 'http://localhost/api/integracoes/sincronizar?dataInicio=32%2F07%2F2026&dataFim=31%2F08%2F2026'],
    ['mês inválido', 'http://localhost/api/integracoes/sincronizar?dataInicio=01%2F13%2F2026&dataFim=31%2F07%2F2026'],
    ['campo vazio', 'http://localhost/api/integracoes/sincronizar?dataInicio=&dataFim=31%2F07%2F2026'],
    ['texto', 'http://localhost/api/integracoes/sincronizar?dataInicio=texto&dataFim=31%2F07%2F2026'],
    ['data final anterior', 'http://localhost/api/integracoes/sincronizar?dataInicio=31%2F07%2F2026&dataFim=01%2F06%2F2026'],
  ])('should_return_400_when_%s', async (_scenario, url) => {
    const response = await POST(postRequest(url));

    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({
      message: 'Informe um período válido no formato dd/MM/yyyy.',
    });
    expect(forwardBackendRequestMock).not.toHaveBeenCalled();
  });

  it.each([401, 403, 500, 503])(
    'should_preserve_backend_status_%s',
    async (status) => {
      forwardBackendRequestMock.mockResolvedValue(Response.json(
        { message: 'Resposta segura do backend.' },
        { status },
      ));

      const response = await POST(postRequest(VALID_URL));

      expect(response.status).toBe(status);
      expect(await response.json()).toEqual({ message: 'Resposta segura do backend.' });
      expect(forwardBackendRequestMock).toHaveBeenCalledTimes(1);
    },
  );

  it('should_leave_jwt_and_authorization_handling_to_the_server_only_bff_helper', async () => {
    await POST(postRequest(VALID_URL));

    const [, init] = forwardBackendRequestMock.mock.calls[0];
    expect(init).toEqual({ method: 'POST' });
    expect(init.headers).toBeUndefined();
    expect(init.body).toBeUndefined();
  });

  it('should_return_sanitized_503_when_backend_forwarding_fails', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    forwardBackendRequestMock.mockRejectedValue(new Error('internal backend url'));

    const response = await POST(postRequest(VALID_URL));

    expect(response.status).toBe(503);
    expect(await response.json()).toEqual({
      message: 'Serviço de sincronização indisponível.',
    });
    expect(consoleError).toHaveBeenCalledWith(
      '[integracoes/sincronizar] Falha ao encaminhar solicitação ao backend.',
    );
  });
});

function postRequest(url: string) {
  return new Request(url, { method: 'POST' });
}

function emptyStreamRequest(url: string) {
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.close();
    },
  });

  return new Request(url, {
    method: 'POST',
    body,
    duplex: 'half',
  } as RequestInit & { duplex: 'half' });
}
