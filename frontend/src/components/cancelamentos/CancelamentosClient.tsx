'use client';

import { useEffect, useState } from 'react';
import { Search } from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { PageHeader } from '@/components/demo/PageHeader';
import { listarCancelamentos } from '@/services/cancelamentos';
import type { Cancelamento, CancelamentoPage } from '@/types/cancelamento';

export function CancelamentosClient() {
  const [page, setPage] = useState<CancelamentoPage | null>(null);
  const [busca, setBusca] = useState('');
  const [origem, setOrigem] = useState('');
  const [status, setStatus] = useState('');
  const [current, setCurrent] = useState(0);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { void load(); }, [current, origem, status]);
  async function load() { try { setError(null); setPage(await listarCancelamentos({ page: current, size: 20, busca, origem, statusCancelamento: status, sort: 'coletadoEm,desc' })); } catch (caught) { setError(caught instanceof Error ? caught.message : 'Não foi possível carregar os cancelamentos.'); } }
  return <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
    <PageHeader title="Cancelamentos" description="Motivos coletados e situação da sincronização" />
    <DemoCard className="mt-3" title="Histórico de cancelamentos" description={page ? `${page.totalElements} registros no filtro atual` : 'Carregando registros'}>
      <div className="flex flex-wrap gap-2 border-b border-clinic-border p-3">
        <label className="flex h-9 min-w-52 flex-1 items-center gap-2 rounded-lg border border-clinic-border bg-clinic-input px-2 text-clinic-muted"><Search className="h-4 w-4" /><input aria-label="Buscar cancelamentos" value={busca} onChange={(e) => setBusca(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') { setCurrent(0); void load(); } }} placeholder="Buscar paciente ou motivo" className="min-w-0 flex-1 bg-transparent text-sm text-clinic-text outline-none" /></label>
        <select aria-label="Filtrar origem" value={origem} onChange={(e) => { setOrigem(e.target.value); setCurrent(0); }} className="h-9 rounded-lg border border-clinic-border bg-clinic-surface px-2 text-sm text-clinic-text"><option value="">Todas as origens</option><option value="LEMBRETE_NEGADO">Lembrete negado</option><option value="PEDIDO_DIRETO">Pedido direto</option><option value="CRM_MANUAL">CRM manual</option><option value="N8N">N8N</option></select>
        <select aria-label="Filtrar status" value={status} onChange={(e) => { setStatus(e.target.value); setCurrent(0); }} className="h-9 rounded-lg border border-clinic-border bg-clinic-surface px-2 text-sm text-clinic-text"><option value="">Todos os status</option><option value="CANCELADO">Cancelado</option><option value="COLETADO">Coletado</option><option value="FALHA_CANCELAMENTO">Falha</option></select>
        <button type="button" onClick={() => { setCurrent(0); void load(); }} className="h-9 rounded-lg bg-clinic-primary px-3 text-sm font-semibold text-white">Aplicar</button>
      </div>
      {error ? <p role="alert" className="p-4 text-sm text-clinic-danger">{error}</p> : null}
      {!error && page?.content.length === 0 ? <p className="p-8 text-center text-sm text-clinic-muted">Nenhum cancelamento encontrado.</p> : null}
      {page?.content.length ? <div className="overflow-x-auto"><table className="min-w-full text-left text-sm"><thead className="border-b border-clinic-border text-clinic-muted"><tr><th className="p-3">Paciente</th><th className="p-3">Agendamento</th><th className="p-3">Profissional</th><th className="p-3">Serviço</th><th className="p-3">Motivo</th><th className="p-3">Origem</th><th className="p-3">Status</th><th className="p-3">Coletado em</th></tr></thead><tbody>{page.content.map((item: Cancelamento) => <tr key={item.id} className="border-b border-clinic-border/70"><td className="p-3 font-medium text-clinic-text">{item.pacienteNome}<span className="block text-xs text-clinic-muted">{item.telefoneMascarado ?? ''}</span></td><td className="p-3 text-clinic-text">{item.dataHoraAgendamento ? new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(item.dataHoraAgendamento)) : '-'}</td><td className="p-3 text-clinic-text">{item.profissional ?? '-'}</td><td className="p-3 text-clinic-text">{item.servico ?? '-'}</td><td className="max-w-xs p-3 text-clinic-text">{item.motivo}</td><td className="p-3 text-clinic-text">{item.origem}</td><td className="p-3 text-clinic-text">{item.statusCancelamento}</td><td className="p-3 text-clinic-muted">{new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(item.coletadoEm))}</td></tr>)}</tbody></table></div> : null}
      {page && page.totalPages > 1 ? <div className="flex justify-end gap-2 p-3"><button type="button" disabled={current === 0} onClick={() => setCurrent((value) => value - 1)} className="rounded border border-clinic-border px-3 py-1 disabled:opacity-50">Anterior</button><span className="py-1 text-sm text-clinic-muted">{current + 1} de {page.totalPages}</span><button type="button" disabled={current + 1 >= page.totalPages} onClick={() => setCurrent((value) => value + 1)} className="rounded border border-clinic-border px-3 py-1 disabled:opacity-50">Próxima</button></div> : null}
    </DemoCard>
  </div>;
}
