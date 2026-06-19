import { Building2, Settings, Shield } from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { PageHeader } from '@/components/demo/PageHeader';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { getClinicaAtual } from '@/services/backend';

export default async function ConfiguracoesPage() {
  const clinica = await getClinicaAtual();

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <PageHeader
        icon={<Settings className="h-4 w-4" />}
        title="Configurações"
        description="Identidade da clínica e políticas de acesso"
      />

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
        <DemoCard title="Clínica atual" description="Identidade do ambiente operacional" icon={<Building2 className="h-5 w-5" />}>
          <div className="space-y-2 px-4 pb-4">
            <ConfigRow label="Nome" value={clinica.nome} />
            <ConfigRow label="Slug" value={clinica.slug} />
            <ConfigRow label="Tipo" value={clinica.tipoClinica === 'ULTRASSONOGRAFIA' ? 'Ultrassonografia' : 'Pré-natal'} />
            <ConfigRow label="Cirurgias na agenda" value={clinica.usaCirurgiasNaAgenda ? 'Ativo' : 'Oculto'} />
          </div>
        </DemoCard>

        <DemoCard title="Acesso e segurança" description="Políticas aplicadas aos usuários internos" icon={<Shield className="h-5 w-5" />}>
          <div className="space-y-2 px-4 pb-4">
            <SecurityRow label="Perfis Gestor, Recepcionista e Médico" />
            <SecurityRow label="Sessão protegida e expiração automática" />
            <SecurityRow label="Logs operacionais sem dados sensíveis" />
          </div>
        </DemoCard>
      </div>
    </div>
  );
}

function ConfigRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between rounded-lg border border-clinic-border bg-clinic-surface-muted px-3 py-2.5">
      <span className="text-[10px] font-bold text-clinic-muted">{label}</span>
      <span className="text-[10px] font-extrabold text-clinic-text">{value}</span>
    </div>
  );
}

function SecurityRow({ label }: { label: string }) {
  return (
    <div className="flex items-center justify-between rounded-lg border border-clinic-border bg-clinic-surface-muted px-3 py-2.5">
      <div className="flex items-center gap-3">
        <Shield className="h-4 w-4 text-clinic-primary" />
        <span className="text-[10px] font-bold text-clinic-text">{label}</span>
      </div>
      <StatusBadge tone="green">Ativo</StatusBadge>
    </div>
  );
}
