import { Plus, Tag } from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { PageHeader } from '@/components/demo/PageHeader';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { demoTags } from '@/mocks/demoOperacional';

export default function TagsPage() {
  return (
    <div className="h-full overflow-auto p-6 custom-scrollbar">
      <PageHeader
        icon={<Tag className="h-5 w-5" />}
        title="Tags"
        description="Classificação visual para leads, pacientes e atendimentos"
        actions={
          <button className="flex h-10 items-center gap-2 rounded-xl bg-clinic-primary px-4 text-sm font-bold text-white">
            <Plus className="h-4 w-4" />
            Nova tag
          </button>
        }
      />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        {demoTags.map((tag) => (
          <DemoCard key={tag.id} title={tag.nome} description={`${tag.uso} usos recentes`}>
            <div className="p-5">
              <StatusBadge tone={tag.cor === 'orange' ? 'orange' : tag.cor === 'pink' ? 'pink' : tag.cor === 'blue' ? 'blue' : 'teal'}>
                {tag.nome}
              </StatusBadge>
              <div className="mt-5 h-2 rounded-full bg-teal-50">
                <div className="h-2 rounded-full bg-clinic-primary" style={{ width: `${Math.min(tag.uso * 4, 100)}%` }} />
              </div>
            </div>
          </DemoCard>
        ))}
      </div>
    </div>
  );
}
