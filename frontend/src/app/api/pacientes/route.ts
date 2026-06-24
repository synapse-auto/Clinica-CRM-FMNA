import { forwardBackendRequest } from '@/services/backend';

export async function GET(request: Request) {
  const search = new URL(request.url).search;
  return forwardBackendRequest(`/api/pacientes${search}`);
}
