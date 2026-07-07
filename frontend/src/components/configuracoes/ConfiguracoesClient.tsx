'use client';

import type { ReactNode } from 'react';
import {
  Activity,
  Building2,
  CheckCircle2,
  Database,
  KeyRound,
  Link2,
  Server,
  Settings,
  ShieldCheck,
} from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { PageHeader } from '@/components/demo/PageHeader';
import { StatusBadge } from '@/components/demo/StatusBadge';
import type { ConfiguracaoResumo } from '@/types/configuracoes';

type ConfiguracoesClientProps = {
  resumo: ConfiguracaoResumo | null;
  error?: string | null;
};

export function ConfiguracoesClient({ resumo, error = null }: ConfiguracoesClientProps) {
  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <PageHeader
        icon={<Settings className="h-4 w-4" />}
        title="Administração"
        description="Configurações administrativas, integrações e políticas internas do CRM"
      />

      {error ? (
        <p role="alert" className="mb-3 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-xs font-semibold text-clinic-danger">
          {error}
        </p>
      ) : null}

      {resumo ? <ConfiguracoesResumoView resumo={resumo} /> : <ResumoIndisponivel />}
    </div>
  );
}

function ConfiguracoesResumoView({ resumo }: { resumo: ConfiguracaoResumo }) {
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-1 gap-3 xl:grid-cols-3">
        <DemoCard
          className="xl:col-span-2"
          title="Identidade da clínica"
          description="Dados seguros do ambiente operacional"
          icon={<Building2 className="h-5 w-5" />}
          actions={<StatusBadge tone="green">{resumo.identidade.statusOperacional}</StatusBadge>}
        >
          <div className="grid gap-2 px-4 pb-4 sm:grid-cols-2">
            <ConfigRow label="Nome" value={resumo.identidade.nome} />
            <ConfigRow label="Slug" value={resumo.identidade.slug} />
            <ConfigRow label="Tipo" value={formatClinicType(resumo.identidade.tipoClinica)} />
            <ConfigRow label="Provider externo" value={resumo.identidade.externalProvider} />
            <BooleanRow label="WhatsApp configurado" active={resumo.identidade.whatsappConfigurado} />
            <BooleanRow label="N8N configurado" active={resumo.identidade.n8nConfigurado} />
          </div>
        </DemoCard>

        <DemoCard
          title="Ambiente"
          description="Estado de execução atual"
          icon={<Server className="h-5 w-5" />}
        >
          <div className="space-y-2 px-4 pb-4">
            <ConfigRow label="Nome" value={resumo.ambiente.nome} />
            <ConfigRow label="Inicializado em" value={formatDateTime(resumo.ambiente.inicializadoEm)} />
          </div>
        </DemoCard>
      </div>

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-12">
        <DemoCard
          className="xl:col-span-7"
          title="Integrações"
          description="Conectores ativos sem exibir credenciais"
          icon={<Link2 className="h-5 w-5" />}
        >
          <div className="space-y-2 px-4 pb-4">
            {resumo.integracoes.map((integracao) => (
              <IntegrationRow key={integracao.nome} integracao={integracao} />
            ))}
          </div>
        </DemoCard>

        <DemoCard
          className="xl:col-span-5"
          title="Última sincronização Medware"
          description="Leitura/importação unidirecional"
          icon={<Database className="h-5 w-5" />}
        >
          <MedwareSync resumo={resumo.ultimaSincronizacaoMedware} />
        </DemoCard>
      </div>

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
        <DemoCard
          title="Segurança e acesso"
          description="Perfis e regras principais do CRM"
          icon={<ShieldCheck className="h-5 w-5" />}
        >
          <div className="space-y-3 px-4 pb-4">
            <div className="flex flex-wrap gap-2">
              {resumo.seguranca.perfisAtivos.length > 0 ? resumo.seguranca.perfisAtivos.map((perfil) => (
                <span
                  key={perfil.perfil}
                  className="rounded-lg border border-clinic-border bg-clinic-surface-muted px-3 py-2 text-xs font-bold text-clinic-text"
                >
                  {formatPerfil(perfil.perfil)}: {perfil.total}
                </span>
              )) : (
                <span className="text-xs font-semibold text-clinic-muted">Nenhum perfil ativo informado</span>
              )}
            </div>
            <div className="grid gap-2">
              {resumo.seguranca.regras.map((regra) => (
                <InfoRow key={regra} icon={<KeyRound className="h-4 w-4" />} label={regra} />
              ))}
            </div>
          </div>
        </DemoCard>

        <DemoCard
          title="Operação"
          description="Regras operacionais já aplicadas"
          icon={<Activity className="h-5 w-5" />}
        >
          <div className="grid gap-2 px-4 pb-4">
            <BooleanRow label="Horários configurados" active={resumo.operacao.horariosConfigurados} />
            <BooleanRow label="IA ativa" active={resumo.operacao.iaAtiva} />
            <BooleanRow label="Retorno HUMANO -> IA em 24h" active={resumo.operacao.retornoHumanoIa24h} />
            <BooleanRow label="Agenda somente leitura para médicos" active={resumo.operacao.agendaMedicoSomenteLeitura} />
            <BooleanRow label="Mutações da agenda restritas" active={resumo.operacao.mutacaoAgendaRestrita} />
          </div>
        </DemoCard>
      </div>
    </div>
  );
}

function ResumoIndisponivel() {
  return (
    <DemoCard title="Resumo indisponível" description="Não foi possível carregar o resumo operacional" icon={<Settings className="h-5 w-5" />}>
      <div className="px-4 pb-4 text-sm font-semibold text-clinic-muted">
        As configurações continuam protegidas; tente recarregar a página após o backend responder.
      </div>
    </DemoCard>
  );
}

function ConfigRow({ label, value }: { label: string; value: string | number | null | undefined }) {
  const displayValue = value === null || value === undefined || value === '' ? 'Indisponível' : value;
  return (
    <div className="flex min-h-11 items-center justify-between gap-3 rounded-lg border border-clinic-border bg-clinic-surface-muted px-3 py-2">
      <span className="text-xs font-bold text-clinic-muted">{label}</span>
      <span className="min-w-0 text-right text-sm font-extrabold text-clinic-text">{displayValue}</span>
    </div>
  );
}

function BooleanRow({ label, active }: { label: string; active: boolean }) {
  return (
    <div className="flex min-h-11 items-center justify-between gap-3 rounded-lg border border-clinic-border bg-clinic-surface-muted px-3 py-2">
      <span className="text-xs font-bold text-clinic-text">{label}</span>
      <StatusBadge tone={active ? 'green' : 'slate'}>{active ? 'Ativo' : 'Não configurado'}</StatusBadge>
    </div>
  );
}

function IntegrationRow({
  integracao,
}: {
  integracao: ConfiguracaoResumo['integracoes'][number];
}) {
  return (
    <div className="rounded-lg border border-clinic-border bg-clinic-surface-muted px-3 py-3">
      <div className="flex items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2">
          <CheckCircle2 className="h-4 w-4 shrink-0 text-clinic-primary" />
          <span className="truncate text-sm font-extrabold text-clinic-text">{integracao.nome}</span>
        </div>
        <StatusBadge tone={statusTone(integracao.status)}>{integracao.status}</StatusBadge>
      </div>
      <p className="mt-1 text-xs font-semibold leading-5 text-clinic-muted">{integracao.detalhe}</p>
    </div>
  );
}

function MedwareSync({ resumo }: { resumo: ConfiguracaoResumo['ultimaSincronizacaoMedware'] }) {
  if (!resumo) {
    return (
      <div className="px-4 pb-4 text-sm font-semibold text-clinic-muted">
        Nenhuma sincronização registrada.
      </div>
    );
  }

  return (
    <div className="space-y-2 px-4 pb-4">
      <div className="flex items-center justify-between rounded-lg border border-clinic-border bg-clinic-surface-muted px-3 py-2">
        <span className="text-xs font-bold text-clinic-muted">Status</span>
        <StatusBadge tone={statusTone(resumo.status)}>{resumo.status}</StatusBadge>
      </div>
      <ConfigRow label="Período" value={formatPeriod(resumo.dataInicio, resumo.dataFim)} />
      <ConfigRow label="Pacientes processados" value={resumo.pacientesProcessados} />
      <ConfigRow label="Agendamentos processados" value={resumo.agendamentosProcessados} />
      <ConfigRow label="Agendamentos ignorados" value={resumo.agendamentosIgnorados} />
      <ConfigRow label="Concluído em" value={formatDateTime(resumo.concluidoEm)} />
      {resumo.erroResumo ? (
        <p className="rounded-lg border border-clinic-orange/25 bg-clinic-orange/10 px-3 py-2 text-xs font-semibold leading-5 text-clinic-orange">
          {resumo.erroResumo}
        </p>
      ) : null}
    </div>
  );
}

function InfoRow({ icon, label }: { icon: ReactNode; label: string }) {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-clinic-border bg-clinic-surface-muted px-3 py-2 text-xs font-bold text-clinic-text">
      <span className="text-clinic-primary">{icon}</span>
      {label}
    </div>
  );
}

function statusTone(status: string): 'green' | 'orange' | 'slate' | 'teal' {
  const normalized = status.toUpperCase();
  if (normalized.includes('CONFIGURADO') || normalized.includes('SUCESSO') || normalized.includes('ATIVO')) {
    return 'green';
  }
  if (normalized.includes('PENDENTE') || normalized.includes('FALHA')) {
    return 'orange';
  }
  if (normalized.includes('DESATIVADO')) {
    return 'slate';
  }
  return 'teal';
}

function formatClinicType(value: string) {
  return value === 'ULTRASSONOGRAFIA' ? 'Ultrassonografia' : 'Pré-natal';
}

function formatPerfil(value: string) {
  return value
    .toLowerCase()
    .replace(/(^|_)([a-z])/g, (_, separator: string, letter: string) => `${separator ? ' ' : ''}${letter.toUpperCase()}`);
}

function formatPeriod(start: string | null, end: string | null) {
  if (!start && !end) return 'Indisponível';
  if (start && end) return `${start} a ${end}`;
  return start ?? end ?? 'Indisponível';
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return 'Indisponível';
  return new Intl.DateTimeFormat('pt-BR', {
    timeZone: 'America/Sao_Paulo',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}
