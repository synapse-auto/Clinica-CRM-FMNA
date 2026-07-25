import { forwardBackendRequest } from '@/services/backend';

export async function GET() {
  return forwardBackendRequest('/api/admin/integracoes/darwin/backfill-agendamentos/status');
}
