import { forwardBackendRequest } from '@/services/backend';

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  return forwardBackendRequest(`/api/pacientes/${id}/tags`);
}
