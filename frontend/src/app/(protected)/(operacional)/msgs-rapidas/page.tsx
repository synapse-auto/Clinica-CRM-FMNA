import { Zap } from 'lucide-react';
import { EmptyState } from '@/components/demo/EmptyState';
import { PageHeader } from '@/components/demo/PageHeader';

export default function MensagensRapidasPage() {
  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <PageHeader
        title="Mensagens Rápidas"
        description="Templates prontos para agilizar atendimentos"
      />

      <EmptyState
        icon={Zap}
        title="Mensagens rápidas em configuração"
        description="Os modelos de mensagem rápida serão habilitados em uma próxima etapa. Nenhum template está disponível no momento."
      />
    </div>
  );
}
