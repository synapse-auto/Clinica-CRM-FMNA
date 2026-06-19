import type { ReactNode } from 'react';
import { AppShell } from '@/components/demo/AppShell';
import { requireSession } from '@/lib/auth/session';
import { getClinicaAtual } from '@/services/backend';

export default async function ProtectedLayout({ children }: { children: ReactNode }) {
  const user = await requireSession();
  const clinic = await getClinicaAtual();
  return (
    <AppShell clinic={clinic} user={user}>
      {children}
    </AppShell>
  );
}
