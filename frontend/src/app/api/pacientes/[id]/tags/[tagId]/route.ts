import { forwardBackendRequest } from '@/services/backend';

export async function POST(
  _request: Request,
  { params }: { params: Promise<{ id: string; tagId: string }> },
) {
  const { id, tagId } = await params;
  return forwardBackendRequest(`/api/pacientes/${id}/tags/${tagId}`, { method: 'POST' });
}

export async function DELETE(
  _request: Request,
  { params }: { params: Promise<{ id: string; tagId: string }> },
) {
  const { id, tagId } = await params;
  return forwardBackendRequest(`/api/pacientes/${id}/tags/${tagId}`, { method: 'DELETE' });
}
