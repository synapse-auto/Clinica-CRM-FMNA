import { Stethoscope } from 'lucide-react';
import { redirect } from 'next/navigation';
import { LoginForm } from '@/components/auth/LoginForm';
import { defaultRouteForProfile } from '@/lib/auth/permissions';
import { getSession } from '@/lib/auth/session';

export default async function LoginPage() {
  const user = await getSession();
  if (user) redirect(defaultRouteForProfile(user.perfil));

  return (
    <main className="flex min-h-dvh items-center justify-center bg-clinic-canvas px-4 py-10">
      <section className="w-full max-w-[410px] rounded-2xl border border-clinic-border bg-clinic-surface p-7 shadow-xl shadow-black/5">
        <div className="flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-clinic-primary text-white">
            <Stethoscope className="h-6 w-6" />
          </div>
          <div>
            <h1 className="text-lg font-extrabold text-clinic-text">Acesso ao CRM</h1>
            <p className="text-[11px] text-clinic-muted">Área interna da clínica</p>
          </div>
        </div>
        <LoginForm />
        <p className="mt-5 text-center text-[10px] leading-4 text-clinic-muted">
          Acesso exclusivo para usuários autorizados pela clínica.
        </p>
      </section>
    </main>
  );
}
