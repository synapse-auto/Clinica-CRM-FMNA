import { Clock } from 'lucide-react';
import { EmptyState } from '@/components/demo/EmptyState';
import { PageHeader } from '@/components/demo/PageHeader';

export default function HorariosPage() {
  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <PageHeader
        icon={<Clock className="h-4 w-4" />}
        title="Horários"
        description="Janelas de funcionamento, IA e atendimento humano"
      />

      <EmptyState
        icon={Clock}
        title="Horários em configuração"
        description="A configuração de janelas de atendimento será habilitada em uma próxima etapa. Nenhum horário está disponível para edição no momento."
      />
    </div>
  );
}
