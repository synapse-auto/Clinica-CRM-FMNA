import type { ReactNode } from 'react';
import { requireSession } from '@/lib/auth/session';

export default async function OperationalLayout({ children }: { children: ReactNode }) {
  await requireSession(['GESTOR', 'RECEPCIONISTA']);
  return children;
}
