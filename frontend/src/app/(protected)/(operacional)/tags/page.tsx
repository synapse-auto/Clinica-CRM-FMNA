import { redirect } from 'next/navigation';
import { TagsClient } from '@/components/operacional/TagsClient';
import { requireSession } from '@/lib/auth/session';
import { getTags, isBackendAuthorizationError } from '@/services/backend';
import type { TagOperacional } from '@/types/operacional';

export default async function TagsPage() {
  const user = await requireSession();
  let tags: TagOperacional[] = [];
  let error: string | null = null;

  try {
    tags = await getTags();
  } catch (caughtError) {
    if (isBackendAuthorizationError(caughtError)) {
      redirect('/login');
    }
    error = 'Não foi possível carregar as tags. Verifique a conexão com o servidor.';
  }

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <TagsClient
        initialTags={tags}
        initialError={error}
        canManage={user.perfil === 'GESTOR'}
      />
    </div>
  );
}
