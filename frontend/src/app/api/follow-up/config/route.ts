import { forwardBackendRequest } from '@/services/backend';

export async function GET() {
  return forwardBackendRequest('/api/follow-up/config');
}

export async function POST(request: Request) {
  return forwardBackendRequest('/api/follow-up/config', {
    method: 'POST',
    body: await request.text(),
    headers: { 'Content-Type': 'application/json' },
  });
}
