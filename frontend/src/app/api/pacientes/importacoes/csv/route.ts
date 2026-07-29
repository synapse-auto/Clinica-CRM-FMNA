import { forwardBackendRequest } from '@/services/backend';

export async function POST(request: Request) {
  const headers = new Headers();
  const contentType = request.headers.get('content-type');
  if (contentType) headers.set('Content-Type', contentType);
  return forwardBackendRequest('/api/pacientes/importacoes/csv', {
    method: 'POST',
    headers,
    body: await request.arrayBuffer(),
  });
}
