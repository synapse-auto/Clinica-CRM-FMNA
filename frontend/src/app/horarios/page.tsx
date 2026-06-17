import { Clock, Save } from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { PageHeader } from '@/components/demo/PageHeader';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { demoHorarios } from '@/mocks/demoOperacional';

export default function HorariosPage() {
  return (
    <div className="h-full overflow-auto p-6 custom-scrollbar">
      <PageHeader
        icon={<Clock className="h-5 w-5" />}
        title="Horários"
        description="Janelas de funcionamento, IA e atendimento humano"
        actions={
          <button className="flex h-10 items-center gap-2 rounded-xl bg-clinic-primary px-4 text-sm font-bold text-white">
            <Save className="h-4 w-4" />
            Salvar horários
          </button>
        }
      />

      <DemoCard title="Grade semanal" description="Configuração visual para homologação">
        <div className="grid grid-cols-1 gap-3 p-5 md:grid-cols-2 xl:grid-cols-3">
          {demoHorarios.map((item) => (
            <div key={item.dia} className="rounded-xl border border-clinic-border bg-white p-4">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="font-extrabold text-clinic-text">{item.dia}</h2>
                <StatusBadge tone={item.ativo ? 'green' : 'slate'}>{item.ativo ? 'Ativo' : 'Fechado'}</StatusBadge>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <input readOnly value={item.inicio} className="h-10 rounded-lg border border-clinic-border bg-teal-50/35 px-3 text-sm font-bold" />
                <input readOnly value={item.fim} className="h-10 rounded-lg border border-clinic-border bg-teal-50/35 px-3 text-sm font-bold" />
              </div>
            </div>
          ))}
        </div>
      </DemoCard>
    </div>
  );
}
