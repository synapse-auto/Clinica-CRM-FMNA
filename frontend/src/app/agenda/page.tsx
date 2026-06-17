import { CalendarDays, Clock, Plus } from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { DemoTable } from '@/components/demo/DemoTable';
import { PageHeader } from '@/components/demo/PageHeader';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { demoAgenda } from '@/mocks/demoOperacional';

export default function AgendaPage() {
  return (
    <div className="h-full overflow-auto p-6 custom-scrollbar">
      <PageHeader
        icon={<CalendarDays className="h-5 w-5" />}
        title="Agenda"
        description="Consultas, exames, retornos e confirmações do dia"
        actions={
          <button className="flex h-10 items-center gap-2 rounded-xl bg-clinic-primary px-4 text-sm font-bold text-white">
            <Plus className="h-4 w-4" />
            Novo agendamento
          </button>
        }
      />

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-12">
        <DemoCard className="xl:col-span-8" title="Agenda de hoje" description="Fila operacional por horário">
          <div className="p-5">
            <DemoTable
              data={demoAgenda}
              getKey={(item) => item.id}
              columns={[
                { key: 'horario', label: 'Horário', render: (item) => <span className="font-extrabold text-clinic-primary">{item.horario}</span> },
                { key: 'paciente', label: 'Paciente', render: (item) => <span className="font-bold text-clinic-text">{item.paciente}</span> },
                { key: 'servico', label: 'Serviço', render: (item) => item.servico },
                { key: 'medico', label: 'Profissional', render: (item) => item.medico },
                { key: 'status', label: 'Status', render: (item) => <StatusBadge tone={statusTone(item.status)}>{item.status}</StatusBadge> },
              ]}
            />
          </div>
        </DemoCard>

        <DemoCard className="xl:col-span-4" title="Resumo" description="Ocupação e confirmações" icon={<Clock className="h-5 w-5" />}>
          <div className="space-y-3 p-5">
            {['08:00 - 10:00', '10:00 - 12:00', '14:00 - 16:00', '16:00 - 18:00'].map((slot, index) => (
              <div key={slot} className="rounded-xl border border-clinic-border bg-teal-50/35 p-4">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-clinic-text">{slot}</span>
                  <span className="text-sm font-extrabold text-clinic-primary">{index + 2} atend.</span>
                </div>
                <div className="mt-3 h-2 rounded-full bg-white">
                  <div className="h-2 rounded-full bg-clinic-primary" style={{ width: `${55 + index * 10}%` }} />
                </div>
              </div>
            ))}
          </div>
        </DemoCard>
      </div>
    </div>
  );
}

function statusTone(status: string) {
  if (status === 'Confirmado') return 'green';
  if (status === 'Pendente') return 'orange';
  if (status === 'Em atendimento') return 'teal';
  return 'slate';
}
