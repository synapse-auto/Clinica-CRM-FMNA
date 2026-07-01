import { forwardBackendRequest } from '@/services/backend';

export async function GET() {
  return forwardBackendRequest('/api/mensagens-festivas/config');
}

export async function POST(request: Request) {
  return forwardBackendRequest('/api/mensagens-festivas/config', {
    method: 'POST',
    body: await request.text(),
    headers: { 'Content-Type': 'application/json' },
  });
}
