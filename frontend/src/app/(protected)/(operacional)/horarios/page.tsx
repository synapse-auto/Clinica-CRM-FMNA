import { redirect } from 'next/navigation';
import { HorariosClient } from '@/components/operacional/HorariosClient';
import { requireSession } from '@/lib/auth/session';
import {
  getAtendentesAtivos,
  getHorarios,
  isBackendAuthorizationError,
} from '@/services/backend';
import type { AtendenteOption } from '@/types/atendimento';
import type { HorarioAtendente } from '@/types/operacional';

export default async function HorariosPage() {
  const user = await requireSession();
  let schedules: HorarioAtendente[] = [];
  let attendants: AtendenteOption[] = [];
  let error: string | null = null;

  try {
    [schedules, attendants] = await Promise.all([
      getHorarios(),
      getAtendentesAtivos(),
    ]);
  } catch (caughtError) {
    if (isBackendAuthorizationError(caughtError)) {
      redirect('/login');
    }
    error = 'Não foi possível carregar os horários. Verifique a conexão com o servidor.';
  }

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <HorariosClient
        initialSchedules={schedules}
        attendants={attendants}
        initialError={error}
        canManage={user.perfil === 'GESTOR'}
      />
    </div>
  );
}
