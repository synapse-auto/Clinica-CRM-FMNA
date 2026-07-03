import { redirect } from 'next/navigation';
import { PacientesClient } from '@/components/pacientes/PacientesClient';
import { requireSession } from '@/lib/auth/session';
import {
  getPacientes,
  getTags,
  isBackendAuthorizationError,
} from '@/services/backend';
import type { TagOperacional } from '@/types/operacional';
import type { PacienteResumo } from '@/types/paciente';

export default async function PacientesPage() {
  const user = await requireSession(['GESTOR', 'RECEPCIONISTA']);
  let pacientes: PacienteResumo[] = [];
  let tags: TagOperacional[] = [];
  let erroCarregamento: string | null = null;

  try {
    [pacientes, tags] = await Promise.all([
      getPacientes(),
      getTags(),
    ]);
  } catch (error) {
    if (isBackendAuthorizationError(error)) {
      redirect('/login');
    }
    erroCarregamento = 'Nao foi possivel carregar os pacientes. Verifique a conexao com o servidor.';
  }

  const tagsAtivas = tags
    .filter((tag) => tag.ativo)
    .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));

  return (
    <PacientesClient
      initialPacientes={pacientes}
      availableTags={tagsAtivas}
      initialError={erroCarregamento}
      canManage={user.perfil === 'GESTOR' || user.perfil === 'RECEPCIONISTA'}
    />
  );
}
