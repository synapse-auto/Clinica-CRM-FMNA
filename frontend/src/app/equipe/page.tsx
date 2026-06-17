import { ShieldCheck, UserPlus } from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { DemoTable } from '@/components/demo/DemoTable';
import { PageHeader } from '@/components/demo/PageHeader';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { demoEquipe } from '@/mocks/demoOperacional';

export default function EquipePage() {
  return (
    <div className="h-full overflow-auto p-6 custom-scrollbar">
      <PageHeader
        icon={<UserPlus className="h-5 w-5" />}
        title="Equipe"
        description="Gestores, recepcionistas e médicos vinculados ao CRM"
        actions={
          <button className="flex h-10 items-center gap-2 rounded-xl bg-clinic-primary px-4 text-sm font-bold text-white">
            <UserPlus className="h-4 w-4" />
            Novo usuário
          </button>
        }
      />

      <DemoCard title="Usuários e permissões" description="Perfis compatíveis com Gestor, Recepcionista e Médico" icon={<ShieldCheck className="h-5 w-5" />}>
        <div className="p-5">
          <DemoTable
            data={demoEquipe}
            getKey={(item) => item.id}
            columns={[
              { key: 'nome', label: 'Nome', render: (item) => <span className="font-extrabold text-clinic-text">{item.nome}</span> },
              { key: 'perfil', label: 'Perfil', render: (item) => <StatusBadge tone={item.perfil === 'GESTOR' ? 'teal' : 'blue'}>{item.perfil}</StatusBadge> },
              { key: 'funcao', label: 'Função', render: (item) => item.funcao },
              { key: 'permissoes', label: 'Permissões', render: (item) => item.permissoes },
              { key: 'status', label: 'Status', render: (item) => <StatusBadge tone={item.status === 'Online' ? 'green' : 'orange'}>{item.status}</StatusBadge> },
            ]}
          />
        </div>
      </DemoCard>
    </div>
  );
}
