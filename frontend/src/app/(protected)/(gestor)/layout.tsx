import type { ReactNode } from 'react';
import { requireSession } from '@/lib/auth/session';

export default async function GestorLayout({ children }: { children: ReactNode }) {
  await requireSession(['GESTOR']);
  return children;
}
