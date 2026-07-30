'use client';

import { CircleStop, LoaderCircle, Plus, Search } from 'lucide-react';
import type {
  AtendimentoFiltroOperacional,
  AtendimentoResumo,
  AtendimentoView,
} from '@/types/atendimento';
import { ContactAvatar } from './ContactAvatar';

type Props = {
  conversations: AtendimentoResumo[];
  activeId: number | null;
  view: AtendimentoView;
  filter: AtendimentoFiltroOperacional;
  type: 'TODOS' | 'IA' | 'HUMANO';
  search: string;
  searching?: boolean;
  error?: string | null;
  onRetry?: () => void;
  onSelect: (id: number) => void;
  onViewChange: (view: AtendimentoView) => void;
  onFilterChange: (
    filter: AtendimentoFiltroOperacional,
    type: 'TODOS' | 'IA' | 'HUMANO',
  ) => void;
  onSearchChange: (value: string) => void;
  canStartManual?: boolean;
  onStartManual?: () => void;
  canCloseAll?: boolean;
  closeAllLoading?: boolean;
  onCloseAll?: () => void;
};

const filters = [
  { label: 'Todos', filter: 'TODOS', type: 'TODOS' },
  { label: 'IA', filter: 'TODOS', type: 'IA' },
  { label: 'Humano', filter: 'TODOS', type: 'HUMANO' },
  { label: 'Meus', filter: 'MEUS', type: 'TODOS' },
  { label: 'Não lidos', filter: 'NAO_LIDOS', type: 'TODOS' },
  { label: 'Aguardando', filter: 'AGUARDANDO', type: 'TODOS' },
  { label: 'Convênio', filter: 'REVISAO', type: 'TODOS' },
] as const;

export function ChatList(props: Props) {
  return (
    <aside
      aria-label="Lista de atendimentos"
      className="flex h-full w-[336px] shrink-0 flex-col border-r border-clinic-border bg-clinic-surface"
      data-testid="chat-list"
    >
      <div className="space-y-4 border-b border-clinic-border px-4 py-5">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="mb-1 text-[10px] font-bold uppercase tracking-[0.12em] text-clinic-primary">CRM · WhatsApp</p>
            <h1 className="text-[19px] font-extrabold tracking-tight text-clinic-text">Atendimentos</h1>
            <p className="mt-1 text-[11px] text-clinic-muted">Conversas reais da clínica</p>
          </div>
          <div className="flex shrink-0 flex-col items-end gap-2">
            <span className="rounded-full border border-clinic-border bg-clinic-soft px-2 py-1 text-[10px] font-bold text-clinic-primary">
              Ao vivo
            </span>
            {props.canStartManual ? (
              <button
                type="button"
                onClick={props.onStartManual}
                className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-clinic-primary px-2.5 text-[10px] font-extrabold text-white hover:brightness-95"
              >
                <Plus className="h-3.5 w-3.5" />
                Novo atendimento
              </button>
            ) : null}
            {props.canCloseAll ? (
              <button
                type="button"
                disabled={props.closeAllLoading}
                onClick={props.onCloseAll}
                aria-label="Encerrar todos os atendimentos"
                title="Encerrar todos os atendimentos"
                className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-clinic-danger/50 px-2.5 text-[10px] font-extrabold text-clinic-danger hover:bg-clinic-danger/10 disabled:opacity-50"
              >
                {props.closeAllLoading ? <LoaderCircle className="h-3.5 w-3.5 animate-spin" /> : <CircleStop className="h-3.5 w-3.5" />}
                <span className="hidden sm:inline">Encerrar todos</span>
                <span className="sm:hidden">Encerrar</span>
              </button>
            ) : null}
          </div>
        </div>
        <div
          role="tablist"
          aria-label="Visão dos atendimentos"
          className="grid grid-cols-2 gap-1 rounded-xl border border-clinic-border bg-clinic-soft p-1"
        >
          <button
            id="atendimentos-ativos-tab"
            type="button"
            role="tab"
            aria-selected={props.view === 'ATIVOS'}
            aria-controls="atendimentos-lista"
            onClick={() => props.onViewChange('ATIVOS')}
            className={`rounded-lg px-2 py-2 text-[11px] font-extrabold transition ${
              props.view === 'ATIVOS'
                ? 'bg-clinic-surface text-clinic-text shadow-sm'
                : 'text-clinic-muted hover:bg-clinic-hover hover:text-clinic-text'
            }`}
          >
            Em atendimento
          </button>
          <button
            id="atendimentos-finalizados-tab"
            type="button"
            role="tab"
            aria-selected={props.view === 'FINALIZADOS'}
            aria-controls="atendimentos-lista"
            onClick={() => props.onViewChange('FINALIZADOS')}
            className={`rounded-lg px-2 py-2 text-[11px] font-extrabold transition ${
              props.view === 'FINALIZADOS'
                ? 'bg-clinic-surface text-clinic-text shadow-sm'
                : 'text-clinic-muted hover:bg-clinic-hover hover:text-clinic-text'
            }`}
          >
            Finalizados
          </button>
        </div>
        {props.view === 'FINALIZADOS' ? (
          <p className="text-[11px] font-medium text-clinic-muted">
            Histórico de atendimentos encerrados
          </p>
        ) : null}
        <label className="relative block" aria-busy={props.searching}>
          <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-clinic-muted" />
          <input
            type="search"
            value={props.search}
            onChange={(event) => props.onSearchChange(event.target.value)}
            placeholder={props.view === 'FINALIZADOS' ? 'Buscar no histórico...' : 'Buscar paciente ou telefone...'}
            className="h-10 w-full rounded-xl border border-clinic-border bg-clinic-input pl-10 pr-10 text-[12px] text-clinic-text outline-none transition placeholder:text-clinic-muted focus:border-clinic-primary focus:ring-4 focus:ring-clinic-primary/10"
          />
          {props.searching ? (
            <LoaderCircle
              aria-hidden="true"
              className="absolute right-3.5 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-clinic-primary"
            />
          ) : null}
          <span className="sr-only" aria-live="polite">
            {props.searching ? 'Pesquisando atendimentos' : ''}
          </span>
        </label>
        {props.error ? (
          <div role="alert" className="flex items-center justify-between gap-2 rounded-lg bg-clinic-danger/10 px-2.5 py-2 text-[10px] font-semibold text-clinic-danger">
            <span className="line-clamp-2">{props.error}</span>
            <button type="button" onClick={props.onRetry} className="shrink-0 font-extrabold underline">
              Tentar novamente
            </button>
          </div>
        ) : null}
        {props.view === 'ATIVOS' ? (
          <div className="flex gap-1.5 overflow-x-auto text-[10px] font-bold hide-scrollbar" aria-label="Filtros de atendimentos ativos">
            {filters.map((item) => {
              const active = props.filter === item.filter && props.type === item.type;
              return (
                <button
                  type="button"
                  key={item.label}
                  onClick={() => props.onFilterChange(item.filter, item.type)}
                  className={`shrink-0 rounded-lg border px-3 py-2 transition ${
                    active
                      ? 'border-clinic-primary bg-clinic-primary text-white shadow-sm'
                      : 'border-clinic-border bg-clinic-surface text-clinic-muted hover:border-clinic-primary/40 hover:bg-clinic-hover hover:text-clinic-text'
                  }`}
                >
                  {item.label}
                </button>
              );
            })}
          </div>
        ) : null}
      </div>

      <div
        id="atendimentos-lista"
        role="tabpanel"
        aria-labelledby={props.view === 'ATIVOS' ? 'atendimentos-ativos-tab' : 'atendimentos-finalizados-tab'}
        className="flex-1 overflow-y-auto custom-scrollbar"
      >
        {props.conversations.length === 0 ? (
            <div className="m-4 rounded-xl border border-dashed border-clinic-border bg-clinic-surface-muted px-4 py-10 text-center">
              <p className="text-[12px] font-bold text-clinic-text">
                {props.search.trim()
                  ? 'Nenhum resultado encontrado.'
                  : props.view === 'FINALIZADOS'
                    ? 'Nenhum atendimento finalizado.'
                    : 'Nenhum atendimento ativo encontrado.'}
              </p>
              <p className="mt-1 text-[11px] text-clinic-muted">
                {props.search.trim()
                  ? 'Ajuste a busca para localizar outro atendimento.'
                  : props.view === 'FINALIZADOS'
                    ? 'Os atendimentos encerrados aparecerão aqui.'
                    : 'Ajuste os filtros ou aguarde uma nova conversa.'}
              </p>
            </div>
        ) : props.conversations.map((chat) => {
          const active = chat.id === props.activeId;
          const attendanceLabel = getAttendanceLabel(chat, props.view);
          const visibleTags = chat.tags.slice(0, 2);
          const hiddenTags = Math.max(chat.tags.length - visibleTags.length, 0);
          return (
            <button
              type="button"
              key={chat.id}
              onClick={() => props.onSelect(chat.id)}
              className={`relative block w-full border-b border-clinic-border/70 px-4 py-4 text-left transition hover:bg-clinic-hover ${
                active ? 'bg-clinic-soft' : 'bg-clinic-surface'
              }`}
            >
              {active ? <span className="absolute inset-y-3 left-0 w-1 rounded-r-full bg-clinic-primary" /> : null}
              <div className="flex gap-3">
                <ContactAvatar name={chat.paciente.nomeBusca} url={chat.paciente.fotoUrl} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-baseline justify-between gap-2">
                    <h2 className="truncate text-[13px] font-extrabold text-clinic-text">
                      {chat.paciente.nomeBusca}
                    </h2>
                    <span className="shrink-0 text-[10px] text-clinic-muted">
                      {formatTime(chat.ultimaMensagemEm)}
                    </span>
                  </div>
                  <p className="mt-1 truncate text-[11px] leading-4 text-clinic-muted">
                    {chat.ultimaMensagemPrevia || 'Sem mensagens'}
                  </p>
                  <p className="mt-1 truncate text-[10px] font-semibold text-clinic-muted">
                    {attendanceLabel}
                  </p>
                  <div className="mt-2 flex flex-wrap items-center gap-1.5 text-[10px]">
                    <span className="rounded-full bg-clinic-blue/10 px-2 py-1 font-bold text-clinic-blue">
                      {props.view === 'FINALIZADOS' ? 'Finalizado' : chat.tratadoPorIa ? 'IA' : 'Humano'}
                    </span>
                    {chat.requerRevisao ? (
                      <span className="rounded-full bg-clinic-warning/10 px-2 py-1 font-bold text-clinic-warning">
                        Convênio
                      </span>
                    ) : null}
                    {chat.naoLidas > 0 ? (
                      <span className="ml-auto min-w-5 rounded-full bg-clinic-primary px-1.5 py-1 text-center font-bold text-white">
                        {chat.naoLidas}
                      </span>
                    ) : null}
                  </div>
                  {visibleTags.length > 0 ? (
                    <div className="mt-2 flex max-w-full flex-wrap gap-1.5 overflow-hidden">
                      {visibleTags.map((tag) => (
                        <span
                          key={tag.id}
                          className="inline-flex max-w-[112px] items-center gap-1 rounded-full border border-clinic-border bg-clinic-soft px-2 py-1 text-[10px] font-bold text-clinic-text"
                        >
                          <span
                            className="h-2 w-2 shrink-0 rounded-full"
                            style={{ backgroundColor: tag.cor ?? 'var(--clinic-muted)' }}
                          />
                          <span className="truncate">{tag.nome}</span>
                        </span>
                      ))}
                      {hiddenTags > 0 ? (
                        <span className="rounded-full border border-clinic-border bg-clinic-soft px-2 py-1 text-[10px] font-bold text-clinic-muted">
                          +{hiddenTags}
                        </span>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              </div>
            </button>
          );
        })}
      </div>
    </aside>
  );
}

function getAttendanceLabel(chat: AtendimentoResumo, view: AtendimentoView) {
  if (view === 'FINALIZADOS') {
    return chat.tratadoPorIa ? 'Encerrado · Atendido por IA' : 'Encerrado · Atendimento humano';
  }
  if (chat.tratadoPorIa) return 'Atendido por IA';
  if (chat.atendentePrincipal) return `Atendido por ${chat.atendentePrincipal.nome}`;
  return 'Humano sem responsável';
}

function formatTime(value: string | null) {
  if (!value) return '';
  return new Intl.DateTimeFormat('pt-BR', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}
