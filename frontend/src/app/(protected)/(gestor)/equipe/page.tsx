import {
  Clock3,
  Mail,
  MessageSquare,
  ShieldCheck,
  Stethoscope,
  UserPlus,
  UsersRound,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { PageHeader } from '@/components/demo/PageHeader';
import {
  demoEquipeGrupos,
  type DemoEquipePessoa,
} from '@/mocks/demoOperacional';

const groupIcons: Record<string, LucideIcon> = {
  gestor: ShieldCheck,
  medicos: Stethoscope,
  recepcionistas: UsersRound,
};

const avatarTones = {
  teal: 'bg-clinic-primary text-white',
  blue: 'bg-clinic-blue text-white',
  purple: 'bg-clinic-indigo text-white',
  cyan: 'bg-clinic-cyan text-white',
  orange: 'bg-clinic-orange text-white',
  pink: 'bg-clinic-pink text-white',
};

export default function EquipePage() {
  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <PageHeader
        title="Equipe"
        description="2 recepcionistas · 4 médicos · 1 gestor"
        actions={
          <button
            type="button"
            className="flex h-8 items-center gap-2 rounded-lg bg-clinic-primary px-3 text-[10px] font-bold text-white transition hover:bg-clinic-primary-strong"
          >
            <UserPlus className="h-3.5 w-3.5" />
            Novo usuário
          </button>
        }
      />

      <div className="space-y-4">
        {demoEquipeGrupos.map((grupo) => (
          <TeamGroup
            key={grupo.id}
            id={grupo.id}
            title={grupo.titulo}
            people={grupo.pessoas}
          />
        ))}
      </div>
    </div>
  );
}

function TeamGroup({
  id,
  title,
  people,
}: {
  id: string;
  title: string;
  people: DemoEquipePessoa[];
}) {
  const Icon = groupIcons[id] ?? UsersRound;

  return (
    <section>
      <div className="mb-2 flex items-center gap-2">
        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-clinic-soft text-clinic-primary">
          <Icon className="h-3.5 w-3.5" />
        </span>
        <h2 className="text-[11px] font-extrabold text-clinic-text">{title}</h2>
        <span className="rounded-full bg-clinic-primary/10 px-2 py-0.5 text-[9px] font-bold text-clinic-primary">
          {people.length}
        </span>
      </div>

      <div className="overflow-hidden rounded-xl border border-clinic-border bg-clinic-surface shadow-[0_1px_2px_rgba(4,32,36,0.04)]">
        {people.map((person) => (
          <TeamMember key={person.id} person={person} groupId={id} />
        ))}
      </div>
    </section>
  );
}

function TeamMember({
  person,
  groupId,
}: {
  person: DemoEquipePessoa;
  groupId: string;
}) {
  return (
    <article className="flex min-h-[72px] flex-col gap-3 border-b border-clinic-border/80 px-4 py-3 last:border-b-0 md:flex-row md:items-center md:justify-between">
      <div className="flex min-w-0 items-center gap-3">
        <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-[11px] font-extrabold shadow-sm ${avatarTones[person.tone]}`}>
          {person.iniciais}
        </span>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="truncate text-[11px] font-extrabold text-clinic-text">{person.nome}</h3>
            {groupId === 'gestor' ? (
              <span className="rounded-full bg-clinic-primary/10 px-2 py-0.5 text-[8px] font-bold text-clinic-primary">
                Gestor
              </span>
            ) : null}
          </div>
          <p className="mt-0.5 text-[9px] text-clinic-muted">{person.funcao}</p>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 md:justify-end">
        <MemberDetails person={person} groupId={groupId} />
        <StatusDot status={person.status} />
      </div>
    </article>
  );
}

function MemberDetails({
  person,
  groupId,
}: {
  person: DemoEquipePessoa;
  groupId: string;
}) {
  if (groupId === 'recepcionistas') {
    return (
      <div className="space-y-1 text-right text-[9px] text-clinic-muted">
        <p className="flex items-center justify-end gap-1">
          <MessageSquare className="h-3 w-3" />
          {person.atendimentosAtivos} ativos
        </p>
        <p className="flex items-center justify-end gap-1">
          <Clock3 className="h-3 w-3" />
          {person.tempoMedio} médio
        </p>
      </div>
    );
  }

  if (groupId === 'gestor') {
    return (
      <div className="space-y-1 text-right text-[9px] text-clinic-muted">
        <p className="flex items-center justify-end gap-1">
          <Mail className="h-3 w-3" />
          {person.email}
        </p>
        <p>{person.telefone}</p>
      </div>
    );
  }

  return (
    <span className="rounded-full bg-clinic-muted/10 px-3 py-1 text-[8px] font-semibold text-clinic-muted">
      {person.acesso}
    </span>
  );
}

function StatusDot({ status }: { status: DemoEquipePessoa['status'] }) {
  const dotTone =
    status === 'Online'
      ? 'bg-clinic-cyan'
      : status === 'Ocupado'
        ? 'bg-clinic-orange'
        : 'bg-clinic-muted';

  return (
    <span className="flex min-w-[62px] items-center justify-end gap-1.5 text-[9px] text-clinic-muted">
      <span className={`h-2 w-2 rounded-full ${dotTone}`} />
      {status}
    </span>
  );
}
