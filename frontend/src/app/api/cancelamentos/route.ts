import { forwardBackendRequest } from '@/services/backend';

export async function GET(request: Request) {
  return forwardBackendRequest(`/api/cancelamentos${new URL(request.url).search}`);
}

export async function DELETE() {
  return forwardBackendRequest('/api/cancelamentos', { method: 'DELETE' });
}
