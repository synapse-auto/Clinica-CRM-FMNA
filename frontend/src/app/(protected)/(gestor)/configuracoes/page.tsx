import { ConfiguracoesClient } from '@/components/configuracoes/ConfiguracoesClient';
import { requireSession } from '@/lib/auth/session';
import {
  getConfiguracoesResumo,
  isBackendAuthorizationError,
} from '@/services/backend';
import type { ConfiguracaoResumo } from '@/types/configuracoes';

export default async function ConfiguracoesPage() {
  const user = await requireSession(['GESTOR']);
  let resumo: ConfiguracaoResumo | null = null;
  let error: string | null = null;

  try {
    resumo = await getConfiguracoesResumo();
  } catch (err) {
    if (isBackendAuthorizationError(err)) {
      throw err;
    }
    error = 'Não foi possível carregar o resumo de configurações.';
  }

  return <ConfiguracoesClient resumo={resumo} error={error} user={user} />;
}
