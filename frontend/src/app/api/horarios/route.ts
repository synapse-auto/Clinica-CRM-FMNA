import { forwardBackendRequest } from '@/services/backend';

export async function GET() {
  return forwardBackendRequest('/api/horarios');
}

export async function POST(request: Request) {
  return forwardBackendRequest('/api/horarios', {
    method: 'POST',
    body: await request.text(),
    headers: { 'Content-Type': 'application/json' },
  });
}
