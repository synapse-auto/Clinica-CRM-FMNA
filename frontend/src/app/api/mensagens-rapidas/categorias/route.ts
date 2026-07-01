import { forwardBackendRequest } from '@/services/backend';

export async function GET() {
  return forwardBackendRequest('/api/mensagens-rapidas/categorias');
}
