import { forwardBackendRequest } from '@/services/backend';

export async function POST(request: Request) {
  return forwardBackendRequest('/api/equipe/usuarios', {
    method: 'POST',
    body: await request.text(),
    headers: { 'Content-Type': 'application/json' },
  });
}
