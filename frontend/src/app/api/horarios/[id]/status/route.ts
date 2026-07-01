import { forwardBackendRequest } from '@/services/backend';

type RouteContext = {
  params: Promise<{ id: string }>;
};

export async function PATCH(request: Request, { params }: RouteContext) {
  const { id } = await params;
  return forwardBackendRequest(`/api/horarios/${id}/status`, {
    method: 'PATCH',
    body: await request.text(),
    headers: { 'Content-Type': 'application/json' },
  });
}
