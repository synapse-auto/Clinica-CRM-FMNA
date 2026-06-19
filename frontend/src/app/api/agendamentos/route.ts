import { forwardBackendRequest } from '@/services/backend';

export async function GET(request: Request) {
  const search = new URL(request.url).search;
  return forwardBackendRequest(`/api/agendamentos${search}`);
}

export async function POST(request: Request) {
  return forwardBackendRequest('/api/agendamentos', {
    method: 'POST',
    body: await request.text(),
    headers: { 'Content-Type': 'application/json' },
  });
}
