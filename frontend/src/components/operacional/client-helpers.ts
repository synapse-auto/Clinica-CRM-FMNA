export type ApiErrorBody = {
  message?: string;
};

export async function safeJson<T>(response: Response): Promise<T | ApiErrorBody | null> {
  try {
    return await response.json() as T | ApiErrorBody;
  } catch {
    return null;
  }
}

export function getResponseMessage(body: ApiErrorBody | unknown, fallback: string) {
  if (body && typeof body === 'object' && 'message' in body) {
    const message = (body as ApiErrorBody).message;
    if (message) return message;
  }
  return fallback;
}

export function isApiErrorBody(body: unknown): body is ApiErrorBody {
  return Boolean(body && typeof body === 'object' && 'message' in body);
}

export function formatTime(value: string) {
  return value.slice(0, 5);
}
