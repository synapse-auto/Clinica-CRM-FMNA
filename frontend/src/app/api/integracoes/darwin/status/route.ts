import { forwardBackendRequest } from '@/services/backend';

export async function GET() {
  return forwardBackendRequest('/api/integracoes/darwin/status');
}
