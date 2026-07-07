'use client';

import {
  AlertCircle,
  Calendar,
  CheckCircle2,
  KeyRound,
  LayoutDashboard,
  Lock,
  MessageSquare,
  Palette,
  ShieldCheck,
  UserCircle,
  Users,
} from 'lucide-react';
import { type FormEvent, type ReactNode, useState } from 'react';
import { DemoCard } from '@/components/demo/DemoCard';
import { PageHeader } from '@/components/demo/PageHeader';
import type { AuthUser } from '@/lib/auth/types';
import type { ClinicaAtualResponse } from '@/types/dashboard';

type MinhaContaClientProps = {
  user: AuthUser;
  clinic: ClinicaAtualResponse;
};

const PASSWORD_RULE_MESSAGE = 'A senha deve ter no mínimo 6 caracteres, contendo letras e números.';
const CRM_PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{6,}$/;

export function MinhaContaClient({ user, clinic }: MinhaContaClientProps) {
  const permissions = buildPermissions(user.perfil);
  const [isPasswordFormOpen, setIsPasswordFormOpen] = useState(false);

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <PageHeader
        eyebrow="Conta"
        title="Minha conta"
        description="Dados do usuário, segurança da sessão e permissões do seu perfil."
        icon={<UserCircle className="h-5 w-5" />}
      />

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-12">
        <DemoCard
          className="xl:col-span-5"
          title="Meu perfil"
          description="Informações básicas da conta autenticada"
          icon={<UserCircle className="h-4 w-4" />}
        >
          <div className="space-y-3 px-4 pb-4">
            <InfoRow label="Nome" value={user.nome} />
            <InfoRow label="E-mail" value={user.email} />
            <InfoRow label="Perfil" value={formatProfile(user.perfil)} />
            <InfoRow label="Clínica vinculada" value={clinic.nome} />
            <InfoRow label="Status da conta" value={user.mustChangePassword ? 'Troca de senha pendente' : 'Ativa'} />
          </div>
        </DemoCard>

        <DemoCard
          className="xl:col-span-7"
          title="Segurança"
          description="Acesso protegido e dados sensíveis fora da interface"
          icon={<ShieldCheck className="h-4 w-4" />}
        >
          <div className="grid gap-3 px-4 pb-4 md:grid-cols-2">
            <StatusTile
              icon={<Lock className="h-4 w-4" />}
              title="Sessão protegida"
              description="O acesso usa autenticação do CRM e expira conforme a política do sistema."
            />
            <StatusTile
              icon={<KeyRound className="h-4 w-4" />}
              title="Senha"
              description="A troca obrigatória é solicitada automaticamente no primeiro acesso."
            >
              <button
                type="button"
                className="mt-3 inline-flex h-9 items-center justify-center rounded-lg bg-clinic-primary px-3 text-xs font-extrabold text-white transition hover:bg-clinic-primary/90 focus:outline-none focus:ring-2 focus:ring-clinic-primary/30"
                onClick={() => setIsPasswordFormOpen((current) => !current)}
              >
                Alterar senha
              </button>
            </StatusTile>
          </div>
          {isPasswordFormOpen ? <PasswordChangeForm /> : null}
        </DemoCard>
      </div>

      <div className="mt-3 grid grid-cols-1 gap-3 xl:grid-cols-12">
        <DemoCard
          className="xl:col-span-5"
          title="Preferências"
          description="Opções já disponíveis na experiência do CRM"
          icon={<Palette className="h-4 w-4" />}
        >
          <div className="px-4 pb-4">
            <StatusTile
              icon={<Palette className="h-4 w-4" />}
              title="Tema claro/escuro"
              description="Use o controle de tema na barra lateral. A preferência visual é aplicada ao app."
            />
          </div>
        </DemoCard>

        <DemoCard
          className="xl:col-span-7"
          title="Permissões"
          description="Resumo do que o seu perfil consegue acessar"
          icon={<ShieldCheck className="h-4 w-4" />}
        >
          <div className="grid gap-2 px-4 pb-4 md:grid-cols-2">
            {permissions.map((item) => (
              <PermissionRow key={item.label} {...item} />
            ))}
          </div>
        </DemoCard>
      </div>
    </div>
  );
}

function PasswordChangeForm() {
  const [senhaAtual, setSenhaAtual] = useState('');
  const [novaSenha, setNovaSenha] = useState('');
  const [confirmacaoNovaSenha, setConfirmacaoNovaSenha] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSuccess(null);

    if (!senhaAtual || !novaSenha || !confirmacaoNovaSenha) {
      setError('Preencha todos os campos para alterar a senha.');
      return;
    }

    if (!CRM_PASSWORD_PATTERN.test(novaSenha)) {
      setError(PASSWORD_RULE_MESSAGE);
      return;
    }

    if (novaSenha !== confirmacaoNovaSenha) {
      setError('As senhas não coincidem.');
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await fetch('/api/auth/change-password', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ senhaAtual, novaSenha, confirmacaoNovaSenha }),
      });
      const payload = await response.json().catch(() => null) as { message?: string } | null;

      if (!response.ok) {
        setError(payload?.message ?? 'Não foi possível alterar a senha.');
        return;
      }

      setSenhaAtual('');
      setNovaSenha('');
      setConfirmacaoNovaSenha('');
      setSuccess('Senha alterada com sucesso.');
    } catch {
      setError('Não foi possível alterar a senha agora.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form
      className="mx-4 mb-4 rounded-lg border border-clinic-border bg-clinic-surface-muted p-4"
      onSubmit={handleSubmit}
    >
      <div className="grid gap-3 md:grid-cols-3">
        <PasswordField id="senhaAtual" label="Senha atual" value={senhaAtual} onChange={setSenhaAtual} />
        <PasswordField id="novaSenha" label="Nova senha" value={novaSenha} onChange={setNovaSenha} />
        <PasswordField
          id="confirmacaoNovaSenha"
          label="Confirmar nova senha"
          value={confirmacaoNovaSenha}
          onChange={setConfirmacaoNovaSenha}
        />
      </div>

      <p className="mt-3 text-xs font-semibold leading-5 text-clinic-muted">{PASSWORD_RULE_MESSAGE}</p>

      {error ? (
        <div
          className="mt-3 flex items-start gap-2 rounded-lg border border-clinic-danger/20 bg-clinic-danger/10 px-3 py-2 text-xs font-semibold text-clinic-danger"
          role="alert"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      ) : null}

      {success ? (
        <div
          className="mt-3 flex items-start gap-2 rounded-lg border border-clinic-success/20 bg-clinic-success/10 px-3 py-2 text-xs font-semibold text-clinic-success"
          role="status"
        >
          <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{success}</span>
        </div>
      ) : null}

      <div className="mt-4 flex justify-end">
        <button
          type="submit"
          className="inline-flex h-10 items-center justify-center rounded-lg bg-clinic-primary px-4 text-sm font-extrabold text-white transition hover:bg-clinic-primary/90 disabled:cursor-not-allowed disabled:opacity-60"
          disabled={isSubmitting}
        >
          {isSubmitting ? 'Salvando...' : 'Salvar nova senha'}
        </button>
      </div>
    </form>
  );
}

function PasswordField({
  id,
  label,
  value,
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="block" htmlFor={id}>
      <span className="text-xs font-bold text-clinic-muted">{label}</span>
      <input
        id={id}
        type="password"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="mt-1 h-10 w-full rounded-lg border border-clinic-border bg-clinic-surface px-3 text-sm font-semibold text-clinic-text outline-none transition focus:border-clinic-primary focus:ring-2 focus:ring-clinic-primary/15"
        maxLength={72}
        required
      />
    </label>
  );
}

function buildPermissions(profile: AuthUser['perfil']) {
  const canOperate = profile === 'GESTOR' || profile === 'RECEPCIONISTA';
  return [
    { icon: <LayoutDashboard className="h-4 w-4" />, label: 'Dashboard', allowed: true },
    { icon: <Calendar className="h-4 w-4" />, label: 'Agenda', allowed: true },
    { icon: <MessageSquare className="h-4 w-4" />, label: 'Atendimentos', allowed: true },
    { icon: <Users className="h-4 w-4" />, label: 'Pacientes', allowed: canOperate },
    { icon: <Calendar className="h-4 w-4" />, label: 'Mutação de agenda', allowed: canOperate },
    ...(profile === 'GESTOR'
      ? [{ icon: <ShieldCheck className="h-4 w-4" />, label: 'Administração do sistema', allowed: true }]
      : []),
  ];
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-clinic-border bg-clinic-surface-muted px-3 py-2.5">
      <p className="text-[10px] font-bold uppercase text-clinic-muted">{label}</p>
      <p className="mt-1 break-words text-sm font-semibold text-clinic-text">{value}</p>
    </div>
  );
}

function StatusTile({
  icon,
  title,
  description,
  children,
}: {
  icon: ReactNode;
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <div className="flex min-h-[104px] gap-3 rounded-lg border border-clinic-border bg-clinic-surface-muted p-3">
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-clinic-soft text-clinic-primary">
        {icon}
      </div>
      <div>
        <h3 className="text-sm font-extrabold text-clinic-text">{title}</h3>
        <p className="mt-1 text-xs leading-5 text-clinic-muted">{description}</p>
        {children}
      </div>
    </div>
  );
}

function PermissionRow({
  icon,
  label,
  allowed,
}: {
  icon: ReactNode;
  label: string;
  allowed: boolean;
}) {
  return (
    <div className="flex min-h-[52px] items-center gap-3 rounded-lg border border-clinic-border bg-clinic-surface-muted px-3 py-2">
      <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${allowed ? 'bg-clinic-success/10 text-clinic-success' : 'bg-clinic-danger/10 text-clinic-danger'}`}>
        {allowed ? <CheckCircle2 className="h-4 w-4" /> : icon}
      </div>
      <div className="min-w-0">
        <p className="truncate text-sm font-bold text-clinic-text">{label}</p>
        <p className="text-[11px] font-semibold text-clinic-muted">{allowed ? 'Permitido' : 'Não disponível para este perfil'}</p>
      </div>
    </div>
  );
}

function formatProfile(profile: AuthUser['perfil']) {
  if (profile === 'RECEPCIONISTA') return 'Recepcionista';
  if (profile === 'MEDICO') return 'Médico';
  return 'Gestor';
}
