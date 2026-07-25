import { forwardBackendRequest } from '@/services/backend';

export async function POST() {
  return forwardBackendRequest('/api/admin/integracoes/darwin/backfill-agendamentos', {
    method: 'POST',
  });
}

export async function DELETE() {
  return forwardBackendRequest('/api/admin/integracoes/darwin/backfill-agendamentos', {
    method: 'DELETE',
  });
}
