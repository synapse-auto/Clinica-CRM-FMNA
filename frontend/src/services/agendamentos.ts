import type { Agendamento, AgendamentoPayload } from '@/types/agendamento';

export function createAgendamento(payload: AgendamentoPayload): Promise<Agendamento> {
  return requestJson('/api/agendamentos', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateAgendamento(
  id: number,
  payload: AgendamentoPayload,
): Promise<Agendamento> {
  return requestJson(`/api/agendamentos/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function cancelAgendamento(id: number, motivo: string): Promise<Agendamento> {
  return requestJson(`/api/agendamentos/${id}/cancelamento`, {
    method: 'PATCH',
    body: JSON.stringify({ motivo }),
  });
}

async function requestJson<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...init.headers,
    },
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  return response.json() as Promise<T>;
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json() as { message?: string };
    return body.message ?? `Não foi possível concluir a operação (${response.status})`;
  } catch {
    return `Não foi possível concluir a operação (${response.status})`;
  }
}
