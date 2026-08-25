'use client';

import { useCallback, useEffect, useState } from 'react';
import { Search, Trash2, X } from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { PageHeader } from '@/components/demo/PageHeader';
import { apagarTodosCancelamentos, listarCancelamentos } from '@/services/cancelamentos';
import type { Cancelamento, CancelamentoPage } from '@/types/cancelamento';

type CancelamentosClientProps = {
  canDelete: boolean;
};

export function CancelamentosClient({ canDelete }: CancelamentosClientProps) {
  const [page, setPage] = useState<CancelamentoPage | null>(null);
  const [busca, setBusca] = useState('');
  const [origem, setOrigem] = useState('');
  const [status, setStatus] = useState('');
  const [current, setCurrent] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const load = useCallback(async (pageNumber = current) => {
    try {
      setError(null);
      setPage(await listarCancelamentos({
        page: pageNumber,
        size: 20,
        busca,
        origem,
        statusCancelamento: status,
        sort: 'coletadoEm,desc',
      }));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Não foi possível carregar os cancelamentos.');
    }
  }, [busca, current, origem, status]);

  useEffect(() => { void load(); }, [load]);

  async function handleDeleteAll() {
    try {
      setDeleteError(null);
      setIsDeleting(true);
      await apagarTodosCancelamentos();
      setIsDeleteModalOpen(false);
      setCurrent(0);
      await load(0);
    } catch (caught) {
      setDeleteError(caught instanceof Error ? caught.message : 'Não foi possível apagar o histórico de cancelamentos.');
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <PageHeader
        title="Cancelamentos"
        description="Motivos coletados e situação da sincronização"
        actions={canDelete ? (
          <button
            type="button"
            onClick={() => { setDeleteError(null); setIsDeleteModalOpen(true); }}
            className="flex h-9 items-center gap-2 rounded-lg border border-clinic-danger/40 px-3 text-sm font-semibold text-clinic-danger hover:bg-clinic-danger/10"
          >
            <Trash2 className="h-4 w-4" />
            Apagar histórico
          </button>
        ) : null}
      />
      <DemoCard className="mt-3" title="Histórico de cancelamentos" description={page ? `${page.totalElements} registros no filtro atual` : 'Carregando registros'}>
        <div className="flex flex-wrap gap-2 border-b border-clinic-border p-3">
          <label className="flex h-9 min-w-52 flex-1 items-center gap-2 rounded-lg border border-clinic-border bg-clinic-input px-2 text-clinic-muted">
            <Search className="h-4 w-4" />
            <input aria-label="Buscar cancelamentos" value={busca} onChange={(e) => setBusca(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') { setCurrent(0); void load(0); } }} placeholder="Buscar paciente ou motivo" className="min-w-0 flex-1 bg-transparent text-sm text-clinic-text outline-none" />
          </label>
          <select aria-label="Filtrar origem" value={origem} onChange={(e) => { setOrigem(e.target.value); setCurrent(0); }} className="h-9 rounded-lg border border-clinic-border bg-clinic-surface px-2 text-sm text-clinic-text">
            <option value="">Todas as origens</option>
            <option value="LEMBRETE_NEGADO">Lembrete negado</option>
            <option value="PEDIDO_DIRETO">Pedido direto</option>
            <option value="CRM_MANUAL">CRM manual</option>
            <option value="N8N">N8N</option>
          </select>
          <select aria-label="Filtrar status" value={status} onChange={(e) => { setStatus(e.target.value); setCurrent(0); }} className="h-9 rounded-lg border border-clinic-border bg-clinic-surface px-2 text-sm text-clinic-text">
            <option value="">Todos os status</option>
            <option value="CANCELADO">Cancelado</option>
            <option value="COLETADO">Coletado</option>
            <option value="FALHA_CANCELAMENTO">Falha</option>
          </select>
          <button type="button" onClick={() => { setCurrent(0); void load(0); }} className="h-9 rounded-lg bg-clinic-primary px-3 text-sm font-semibold text-white">Aplicar</button>
        </div>
        {error ? <p role="alert" className="p-4 text-sm text-clinic-danger">{error}</p> : null}
        {!error && page?.content.length === 0 ? <p className="p-8 text-center text-sm text-clinic-muted">Nenhum cancelamento encontrado.</p> : null}
        {page?.content.length ? (
          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead className="border-b border-clinic-border text-clinic-muted"><tr><th className="p-3">Paciente</th><th className="p-3">Agendamento</th><th className="p-3">Profissional</th><th className="p-3">Serviço</th><th className="p-3">Motivo</th><th className="p-3">Status</th><th className="p-3">Coletado em</th></tr></thead>
              <tbody>{page.content.map((item: Cancelamento) => <tr key={item.id} className="border-b border-clinic-border/70"><td className="p-3 font-medium text-clinic-text">{item.pacienteNome}<span className="block text-xs text-clinic-muted">{item.telefoneMascarado ?? ''}</span></td><td className="p-3 text-clinic-text">{item.dataHoraAgendamento ? new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(item.dataHoraAgendamento)) : '-'}</td><td className="p-3 text-clinic-text">{item.profissional ?? '-'}</td><td className="p-3 text-clinic-text">{item.servico ?? '-'}</td><td className="max-w-xs p-3 text-clinic-text">{item.motivo}</td><td className="p-3 text-clinic-text">{item.statusCancelamento}</td><td className="p-3 text-clinic-muted">{new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(item.coletadoEm))}</td></tr>)}</tbody>
            </table>
          </div>
        ) : null}
        {page && page.totalPages > 1 ? <div className="flex justify-end gap-2 p-3"><button type="button" disabled={current === 0} onClick={() => setCurrent((value) => value - 1)} className="rounded border border-clinic-border px-3 py-1 disabled:opacity-50">Anterior</button><span className="py-1 text-sm text-clinic-muted">{current + 1} de {page.totalPages}</span><button type="button" disabled={current + 1 >= page.totalPages} onClick={() => setCurrent((value) => value + 1)} className="rounded border border-clinic-border px-3 py-1 disabled:opacity-50">Próxima</button></div> : null}
      </DemoCard>
      {isDeleteModalOpen ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4">
          <section role="dialog" aria-modal="true" aria-labelledby="delete-cancelamentos-title" className="w-full max-w-md rounded-2xl border border-clinic-border bg-clinic-surface p-5 shadow-xl">
            <div className="flex items-start justify-between gap-3">
              <div><h2 id="delete-cancelamentos-title" className="text-base font-extrabold text-clinic-text">Apagar histórico de cancelamentos?</h2><p className="mt-2 text-sm text-clinic-muted">Todos os registros de cancelamento desta clínica serão removidos. Essa ação não pode ser desfeita.</p></div>
              <button type="button" aria-label="Fechar" disabled={isDeleting} onClick={() => setIsDeleteModalOpen(false)} className="rounded p-1 text-clinic-muted hover:bg-clinic-soft disabled:opacity-50"><X className="h-4 w-4" /></button>
            </div>
            {deleteError ? <p role="alert" className="mt-3 text-sm text-clinic-danger">{deleteError}</p> : null}
            <div className="mt-5 flex justify-end gap-2"><button type="button" disabled={isDeleting} onClick={() => setIsDeleteModalOpen(false)} className="rounded-lg border border-clinic-border px-3 py-2 text-sm font-semibold text-clinic-text disabled:opacity-50">Cancelar</button><button type="button" disabled={isDeleting} onClick={() => void handleDeleteAll()} className="rounded-lg bg-clinic-danger px-3 py-2 text-sm font-semibold text-white disabled:opacity-50">{isDeleting ? 'Apagando…' : 'Apagar histórico'}</button></div>
          </section>
        </div>
      ) : null}
    </div>
  );
}
