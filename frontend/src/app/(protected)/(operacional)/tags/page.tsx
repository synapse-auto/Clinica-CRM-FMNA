import { Tag } from 'lucide-react';
import { EmptyState } from '@/components/demo/EmptyState';
import { PageHeader } from '@/components/demo/PageHeader';

export default function TagsPage() {
  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <PageHeader
        title="Tags"
        description="Gerencie as etiquetas para organizar seus leads"
      />

      <EmptyState
        icon={Tag}
        title="Tags em configuração"
        description="O gerenciamento de tags será habilitado em uma próxima etapa. Nenhuma etiqueta está disponível no momento."
      />
    </div>
  );
}
