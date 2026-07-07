import {
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
import type { ReactNode } from 'react';
import { DemoCard } from '@/components/demo/DemoCard';
import { PageHeader } from '@/components/demo/PageHeader';
import type { AuthUser } from '@/lib/auth/types';
import type { ClinicaAtualResponse } from '@/types/dashboard';

type MinhaContaClientProps = {
  user: AuthUser;
  clinic: ClinicaAtualResponse;
};

export function MinhaContaClient({ user, clinic }: MinhaContaClientProps) {
  const permissions = buildPermissions(user.perfil);

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
            />
          </div>
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
}: {
  icon: ReactNode;
  title: string;
  description: string;
}) {
  return (
    <div className="flex min-h-[104px] gap-3 rounded-lg border border-clinic-border bg-clinic-surface-muted p-3">
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-clinic-soft text-clinic-primary">
        {icon}
      </div>
      <div>
        <h3 className="text-sm font-extrabold text-clinic-text">{title}</h3>
        <p className="mt-1 text-xs leading-5 text-clinic-muted">{description}</p>
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
