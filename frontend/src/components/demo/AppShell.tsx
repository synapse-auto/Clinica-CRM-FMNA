import type { ReactNode } from 'react';
import type { ClinicaAtualResponse } from '@/types/dashboard';
import { DemoSidebar } from './DemoSidebar';

type AppShellProps = {
  clinic: ClinicaAtualResponse;
  children: ReactNode;
};

export function AppShell({ clinic, children }: AppShellProps) {
  return (
    <div className="flex h-dvh min-h-screen w-full overflow-hidden bg-clinic-canvas text-clinic-text">
      <DemoSidebar clinic={clinic} />
      <main className="min-w-0 flex-1 overflow-hidden bg-clinic-canvas">{children}</main>
    </div>
  );
}
