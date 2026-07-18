import { forwardBackendRequest } from '@/services/backend';

type RouteContext = {
  params: Promise<{ usuarioId: string }>;
};

export async function PATCH(request: Request, context: RouteContext) {
  const { usuarioId } = await context.params;
  return forwardBackendRequest(
    `/api/equipe/usuarios/${encodeURIComponent(usuarioId)}/permissao-gerenciamento`,
    {
      method: 'PATCH',
      body: await request.text(),
      headers: { 'Content-Type': 'application/json' },
    },
  );
}
