import { forwardBackendRequest } from '@/services/backend';

export async function GET(request: Request) {
  return forwardBackendRequest(`/api/cancelamentos${new URL(request.url).search}`);
}
