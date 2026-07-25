import { forwardBackendRequest } from '@/services/backend';

type RouteContext = {
  params: Promise<{ id: string }>;
};

export async function GET(_request: Request, { params }: RouteContext) {
  const { id } = await params;
  return forwardBackendRequest(`/api/agenda/${id}`);
}

export async function PUT(request: Request, { params }: RouteContext) {
  const { id } = await params;
  return forwardBackendRequest(`/api/agenda/${id}`, {
    method: 'PUT',
    body: await request.text(),
    headers: { 'Content-Type': 'application/json' },
  });
}

export async function DELETE(request: Request, { params }: RouteContext) {
  const { id } = await params;
  const search = new URL(request.url).search;
  return forwardBackendRequest(`/api/agenda/${id}${search}`, {
    method: 'DELETE',
  });
}
