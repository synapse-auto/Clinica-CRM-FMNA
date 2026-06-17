import { Download, Search, Users } from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { DemoTable } from '@/components/demo/DemoTable';
import { PageHeader } from '@/components/demo/PageHeader';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { demoPacientes } from '@/mocks/demoOperacional';

export default function PacientesPage() {
  return (
    <div className="h-full overflow-auto p-6 custom-scrollbar">
      <PageHeader
        icon={<Users className="h-5 w-5" />}
        title="Pacientes"
        description="Base de contatos, status, origem e último relacionamento"
        actions={
          <button className="flex h-10 items-center gap-2 rounded-xl border border-clinic-border bg-white px-4 text-sm font-bold text-clinic-text">
            <Download className="h-4 w-4" />
            Exportar
          </button>
        }
      />

      <DemoCard title="Lista de pacientes" description="Fallback visual até a API completa de pacientes">
        <div className="space-y-4 p-5">
          <label className="relative block max-w-md">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-clinic-muted" />
            <input
              placeholder="Buscar por nome, telefone ou tag..."
              className="h-10 w-full rounded-lg border border-clinic-border bg-teal-50/40 pl-9 pr-3 text-sm outline-none focus:border-clinic-primary"
            />
          </label>
          <DemoTable
            data={demoPacientes}
            getKey={(item) => item.id}
            columns={[
              { key: 'nome', label: 'Paciente', render: (item) => <span className="font-extrabold text-clinic-text">{item.nome}</span> },
              { key: 'telefone', label: 'Telefone', render: (item) => item.telefone },
              { key: 'origem', label: 'Origem', render: (item) => item.origem },
              { key: 'ultimo', label: 'Último contato', render: (item) => item.ultimoContato },
              { key: 'tags', label: 'Tags', render: (item) => <div className="flex flex-wrap gap-1">{item.tags.map((tag) => <StatusBadge key={tag} tone="blue">{tag}</StatusBadge>)}</div> },
              { key: 'status', label: 'Status', render: (item) => <StatusBadge tone={item.status === 'Follow UP' ? 'orange' : 'teal'}>{item.status}</StatusBadge> },
            ]}
          />
        </div>
      </DemoCard>
    </div>
  );
}
