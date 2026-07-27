import { forwardBackendRequest } from '@/services/backend';

export async function GET(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const ifNoneMatch = request.headers.get('if-none-match');
  const headers: Record<string, string> = {
    Accept: 'image/jpeg,image/png,image/webp',
  };
  if (ifNoneMatch) headers['If-None-Match'] = ifNoneMatch;
  return forwardBackendRequest(`/api/pacientes/${encodeURIComponent(id)}/foto`, { headers });
}
