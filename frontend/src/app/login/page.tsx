import Image from 'next/image';
import { redirect } from 'next/navigation';
import { LoginForm } from '@/components/auth/LoginForm';
import { routeAfterAuthentication } from '@/lib/auth/permissions';
import { getSession } from '@/lib/auth/session';

export default async function LoginPage() {
  const user = await getSession();
  if (user) redirect(routeAfterAuthentication(user.perfil, user.mustChangePassword));

  return (
    <main className="flex min-h-dvh items-center justify-center bg-clinic-canvas px-4 py-10">
      <section className="w-full max-w-[410px] rounded-2xl border border-clinic-border bg-clinic-surface p-7 shadow-xl shadow-black/5">
        <div className="flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-white p-1 shadow-sm">
            <Image src="/ultramedical-logo.png" alt="UltraMedical" width={40} height={40} className="object-contain" />
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
