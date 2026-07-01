import { forwardBackendRequest } from '@/services/backend';

type RouteContext = {
  params: Promise<{ path?: string[] }>;
};

export async function GET(request: Request, context: RouteContext) {
  return forward(request, context);
}

export async function POST(request: Request, context: RouteContext) {
  return forward(request, context);
}

export async function PATCH(request: Request, context: RouteContext) {
  return forward(request, context);
}

export async function DELETE(request: Request, context: RouteContext) {
  return forward(request, context);
}

async function forward(request: Request, { params }: RouteContext) {
  const { path = [] } = await params;
  const url = new URL(request.url);
  const target = `/api/atendimentos${path.length ? `/${path.join('/')}` : ''}${url.search}`;
  const headers = new Headers();
  const contentType = request.headers.get('content-type');
  if (contentType) headers.set('Content-Type', contentType);
  const accept = request.headers.get('accept');
  if (accept) headers.set('Accept', accept);
  const hasBody = request.method !== 'GET' && request.method !== 'HEAD';
  return forwardBackendRequest(target, {
    method: request.method,
    headers,
    body: hasBody ? await request.arrayBuffer() : undefined,
  });
}
