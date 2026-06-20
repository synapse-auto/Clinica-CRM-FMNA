import { KeyRound } from 'lucide-react';
import { redirect } from 'next/navigation';
import { ChangePasswordForm } from '@/components/auth/ChangePasswordForm';
import { defaultRouteForProfile } from '@/lib/auth/permissions';
import { getSession } from '@/lib/auth/session';

export default async function ChangePasswordPage() {
  const user = await getSession();
  if (!user) redirect('/login');
  if (!user.mustChangePassword) redirect(defaultRouteForProfile(user.perfil));

  return (
    <main className="flex min-h-dvh items-center justify-center bg-clinic-canvas px-4 py-10">
      <section className="w-full max-w-[440px] rounded-2xl border border-clinic-border bg-clinic-surface p-7 shadow-xl shadow-black/5">
        <div className="flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-clinic-primary text-white">
            <KeyRound className="h-6 w-6" />
          </div>
          <div>
            <h1 className="text-lg font-extrabold text-clinic-text">Crie uma nova senha</h1>
            <p className="text-[11px] text-clinic-muted">
              A troca é obrigatória antes do primeiro acesso ao CRM.
            </p>
          </div>
        </div>
        <ChangePasswordForm />
      </section>
    </main>
  );
}
