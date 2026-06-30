import { redirect } from 'next/navigation';
import { EquipeClient } from '@/components/equipe/EquipeClient';
import { getEquipe, isBackendAuthorizationError } from '@/services/backend';
import type { EquipeResponse } from '@/types/equipe';

const EMPTY_EQUIPE: EquipeResponse = {
  grupos: [
    { perfil: 'GESTOR', titulo: 'Gestores', usuarios: [] },
    { perfil: 'MEDICO', titulo: 'Médicos', usuarios: [] },
    { perfil: 'RECEPCIONISTA', titulo: 'Recepcionistas', usuarios: [] },
  ],
};

export default async function EquipePage() {
  let equipe = EMPTY_EQUIPE;
  let erroCarregamento: string | null = null;

  try {
    equipe = await getEquipe();
  } catch (error) {
    if (isBackendAuthorizationError(error)) {
      redirect('/login');
    }
    erroCarregamento = 'Não foi possível carregar a equipe. Verifique a conexão com o servidor.';
  }

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <EquipeClient initialData={equipe} initialError={erroCarregamento} />
    </div>
  );
}
