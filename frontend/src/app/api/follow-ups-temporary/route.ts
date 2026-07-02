import { forwardBackendRequest } from '@/services/backend';

type FollowUpsTemporaryPage<T> = {
  content?: T[];
};

export async function GET() {
  try {
    const upstream = await forwardBackendRequest('/api/follow-ups-temporary');

    if (!upstream.ok) {
      console.warn(`[follow-ups-temporary] Backend respondeu ${upstream.status}.`);
      return Response.json(
        { message: 'Fila temporaria de follow-ups indisponivel.' },
        { status: upstream.status >= 500 ? 502 : upstream.status },
      );
    }

    const body = await safeJson<FollowUpsTemporaryPage<unknown> | unknown[]>(upstream);
    return Response.json(normalizeQueue(body));
  } catch (error) {
    console.warn('[follow-ups-temporary] Falha ao consultar backend.', safeErrorMessage(error));
    return Response.json(
      { message: 'Fila temporaria de follow-ups indisponivel.' },
      { status: 502 },
    );
  }
}

async function safeJson<T>(response: Response): Promise<T | null> {
  try {
    return await response.json() as T;
  } catch {
    return null;
  }
}

function normalizeQueue<T>(body: FollowUpsTemporaryPage<T> | T[] | null) {
  if (Array.isArray(body)) {
    return body;
  }
  return body?.content ?? [];
}

function safeErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'erro desconhecido';
}
