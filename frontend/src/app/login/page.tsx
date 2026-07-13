import Image from 'next/image';
import { redirect } from 'next/navigation';
import { Activity, MessageCircle, ShieldCheck } from 'lucide-react';
import { LoginForm } from '@/components/auth/LoginForm';
import { brandingInitials, publicBranding, publicDocumentTitle } from '@/config/public-branding';
import { routeAfterAuthentication } from '@/lib/auth/permissions';
import { getSession } from '@/lib/auth/session';
import styles from './login.module.css';

export default async function LoginPage() {
  const user = await getSession();
  if (user) redirect(routeAfterAuthentication(user.perfil, user.mustChangePassword));

  return (
    <main className={styles.shell}>
      <section className={styles.brandPanel} aria-labelledby="login-brand-headline">
        <header className={styles.brandHeader}>
          <div className={styles.brandMark}>
            {publicBranding.logoUrl ? (
              <Image src={publicBranding.logoUrl} alt={publicBranding.clinicName} width={48} height={48} priority />
            ) : (
              <span aria-hidden="true">{brandingInitials(publicBranding.clinicName)}</span>
            )}
          </div>
          <div>
            <p className={styles.brandName}>{publicBranding.clinicName}</p>
            <p className={styles.brandType}>{publicDocumentTitle}</p>
          </div>
        </header>

        <div className={styles.institutionalCopy}>
          <p className={styles.eyebrow}>Operação em um só lugar</p>
          <h1 id="login-brand-headline" className={styles.headline}>{publicBranding.headline}</h1>
          <p className={styles.description}>{publicBranding.description}</p>
          <ul className={styles.benefits}>
            {publicBranding.benefits.map((benefit, index) => {
              const Icon = [Activity, MessageCircle, ShieldCheck][index];
              return (
                <li key={benefit} className={styles.benefit}>
                  <span className={styles.benefitIcon} aria-hidden="true"><Icon className="h-4 w-4" /></span>
                  <span>{benefit}</span>
                </li>
              );
            })}
          </ul>
        </div>
      </section>

      <section className={styles.formPanel} aria-labelledby="login-title">
        <div className={styles.formFrame}>
          <p className={styles.formEyebrow}>Acesso seguro</p>
          <h2 id="login-title" className={styles.formTitle}>Acesse sua conta</h2>
          <p className={styles.formSubtitle}>Entre com suas credenciais para continuar.</p>
          <LoginForm />
          <p className={styles.securityNote}>Acesso exclusivo para usuários autorizados pela clínica.</p>
        </div>
      </section>
    </main>
  );
}
