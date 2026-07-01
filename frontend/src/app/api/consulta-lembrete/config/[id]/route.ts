import { forwardBackendRequest } from '@/services/backend';

type RouteContext = {
  params: Promise<{ id: string }>;
};

export async function PUT(request: Request, { params }: RouteContext) {
  const { id } = await params;
  return forwardBackendRequest(`/api/consulta-lembrete/config/${id}`, {
    method: 'PUT',
    body: await request.text(),
    headers: { 'Content-Type': 'application/json' },
  });
}
