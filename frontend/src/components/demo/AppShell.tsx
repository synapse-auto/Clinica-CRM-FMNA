import type { ReactNode } from 'react';
import type { ClinicaAtualResponse } from '@/types/dashboard';
import type { AuthUser } from '@/lib/auth/types';
import { DemoSidebar } from './DemoSidebar';

type AppShellProps = {
  clinic: ClinicaAtualResponse;
  user: AuthUser;
  children: ReactNode;
};

export function AppShell({ clinic, user, children }: AppShellProps) {
  return (
    <div className="app-scale-comfortable flex h-dvh min-h-screen w-full overflow-hidden bg-clinic-canvas text-clinic-text transition-colors duration-200">
      <DemoSidebar clinic={clinic} user={user} />
      <main className="min-w-0 flex-1 overflow-hidden bg-clinic-canvas">{children}</main>
    </div>
  );
}
