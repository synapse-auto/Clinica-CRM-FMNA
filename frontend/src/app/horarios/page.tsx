import { Clock, Save } from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { PageHeader } from '@/components/demo/PageHeader';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { demoHorarioIa } from '@/mocks/demoOperacional';

export default function HorariosPage() {
  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <PageHeader
        icon={<Clock className="h-4 w-4" />}
        title="Horários"
        description="Janelas de funcionamento, IA e atendimento humano"
        actions={
          <button className="flex h-8 items-center gap-2 rounded-lg bg-clinic-primary px-3 text-[10px] font-bold text-white">
            <Save className="h-3.5 w-3.5" />
            Salvar horários
          </button>
        }
      />

      <DemoCard title="Grade semanal" description="Configuração visual para homologação">
        <div className="grid grid-cols-1 gap-3 px-4 pb-4 md:grid-cols-2 xl:grid-cols-3">
          {demoHorarioIa.dias.map((dia) => (
            <div key={dia} className="rounded-lg border border-clinic-border bg-clinic-surface-muted p-3">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="font-extrabold text-clinic-text">{dia}</h2>
                <StatusBadge tone="green">{demoHorarioIa.atendimento24h ? '24 horas' : 'Ativo'}</StatusBadge>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <input readOnly value={demoHorarioIa.inicio} className="h-9 rounded-lg border border-clinic-border bg-clinic-input px-3 text-[10px] font-bold text-clinic-text" />
                <input readOnly value={demoHorarioIa.fim} className="h-9 rounded-lg border border-clinic-border bg-clinic-input px-3 text-[10px] font-bold text-clinic-text" />
              </div>
            </div>
          ))}
        </div>
      </DemoCard>
    </div>
  );
}
