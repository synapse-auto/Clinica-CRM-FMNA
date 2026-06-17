import { Bot, Building2, Link2, MessageCircle, Settings, Shield } from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { PageHeader } from '@/components/demo/PageHeader';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { getClinicaAtual } from '@/services/backend';

export default async function ConfiguracoesPage() {
  const clinica = await getClinicaAtual();

  return (
    <div className="h-full overflow-auto p-6 custom-scrollbar">
      <PageHeader
        icon={<Settings className="h-5 w-5" />}
        title="Configurações"
        description="Clínica, WhatsApp, integrações, IA e permissões"
      />

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <DemoCard title="Clínica atual" description="Identidade vinda da configuração multi-clínica" icon={<Building2 className="h-5 w-5" />}>
          <div className="space-y-4 p-5">
            <ConfigRow label="Nome" value={clinica.nome} />
            <ConfigRow label="Slug" value={clinica.slug} />
            <ConfigRow label="Tipo" value={clinica.tipoClinica === 'ULTRASSONOGRAFIA' ? 'Ultrassonografia' : 'Pré-natal'} />
            <ConfigRow label="Cirurgias na agenda" value={clinica.usaCirurgiasNaAgenda ? 'Ativo' : 'Oculto'} />
          </div>
        </DemoCard>

        <DemoCard title="WhatsApp Oficial" description="Tokens não são exibidos no frontend" icon={<MessageCircle className="h-5 w-5" />}>
          <div className="space-y-4 p-5">
            <ConfigRow label="Canal" value="Meta Cloud API" />
            <ConfigRow label="Webhook" value="Configurado no backend" />
            <ConfigRow label="Credenciais" value="Protegidas por variável de ambiente" />
          </div>
        </DemoCard>

        <DemoCard title="Integrações" description="Providers externos read-only" icon={<Link2 className="h-5 w-5" />}>
          <div className="space-y-3 p-5">
            <IntegrationRow label="Darwin" active={clinica.slug === 'fmna'} />
            <IntegrationRow label="Medware" active={clinica.slug === 'ultramedical'} />
            <IntegrationRow label="N8N" active={clinica.followUpAutomatico} />
          </div>
        </DemoCard>

        <DemoCard title="IA e permissões" description="Controles visuais para homologação" icon={<Bot className="h-5 w-5" />}>
          <div className="space-y-3 p-5">
            <IntegrationRow label="Follow-up automático" active={clinica.followUpAutomatico} />
            <IntegrationRow label="Perfis Gestor/Recepcionista/Médico" active />
            <IntegrationRow label="LGPD e logs sem PII" active />
          </div>
        </DemoCard>
      </div>
    </div>
  );
}

function ConfigRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between rounded-xl border border-clinic-border bg-teal-50/30 px-4 py-3">
      <span className="text-sm font-bold text-clinic-muted">{label}</span>
      <span className="text-sm font-extrabold text-clinic-text">{value}</span>
    </div>
  );
}

function IntegrationRow({ label, active }: { label: string; active: boolean }) {
  return (
    <div className="flex items-center justify-between rounded-xl border border-clinic-border bg-white px-4 py-3">
      <div className="flex items-center gap-3">
        <Shield className="h-4 w-4 text-clinic-primary" />
        <span className="text-sm font-bold text-clinic-text">{label}</span>
      </div>
      <StatusBadge tone={active ? 'green' : 'slate'}>{active ? 'Ativo' : 'Pendente'}</StatusBadge>
    </div>
  );
}
