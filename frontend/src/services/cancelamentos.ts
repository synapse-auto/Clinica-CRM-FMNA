import type { CancelamentoPage } from '@/types/cancelamento';

export async function listarCancelamentos(params: Record<string, string | number | undefined>): Promise<CancelamentoPage> {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => { if (value !== undefined && value !== '') search.set(key, String(value)); });
  const response = await fetch(`/api/cancelamentos?${search.toString()}`, { headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error('Não foi possível carregar os cancelamentos.');
  return response.json() as Promise<CancelamentoPage>;
}
