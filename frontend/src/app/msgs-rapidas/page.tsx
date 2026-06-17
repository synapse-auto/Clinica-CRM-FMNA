import { MessageCircleQuestion, Plus } from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { DemoTable } from '@/components/demo/DemoTable';
import { PageHeader } from '@/components/demo/PageHeader';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { demoMensagensRapidas } from '@/mocks/demoOperacional';

export default function MensagensRapidasPage() {
  return (
    <div className="h-full overflow-auto p-6 custom-scrollbar">
      <PageHeader
        icon={<MessageCircleQuestion className="h-5 w-5" />}
        title="Mensagens Rápidas"
        description="Templates reutilizáveis para a equipe de atendimento"
        actions={
          <button className="flex h-10 items-center gap-2 rounded-xl bg-clinic-primary px-4 text-sm font-bold text-white">
            <Plus className="h-4 w-4" />
            Novo template
          </button>
        }
      />

      <DemoCard title="Templates" description="Mensagens prontas para conversas WhatsApp">
        <div className="p-5">
          <DemoTable
            data={demoMensagensRapidas}
            getKey={(item) => item.id}
            columns={[
              { key: 'titulo', label: 'Título', render: (item) => <span className="font-extrabold text-clinic-text">{item.titulo}</span> },
              { key: 'categoria', label: 'Categoria', render: (item) => <StatusBadge tone="teal">{item.categoria}</StatusBadge> },
              { key: 'texto', label: 'Mensagem', render: (item) => <span className="line-clamp-2 text-clinic-muted">{item.texto}</span> },
            ]}
          />
        </div>
      </DemoCard>
    </div>
  );
}
