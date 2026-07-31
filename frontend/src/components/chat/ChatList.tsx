'use client';

import {
  Check,
  ChevronDown,
  CircleStop,
  Ellipsis,
  LoaderCircle,
  MessageCircle,
  Plus,
  Search,
} from 'lucide-react';
import { useEffect, useRef } from 'react';
import { Menu } from '@base-ui/react/menu';
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
  onCloseAllTriggerReady?: (focus: () => void) => void;
};

const primaryFilters = [
  { label: 'Todos', filter: 'TODOS', type: 'TODOS' },
  { label: 'IA', filter: 'TODOS', type: 'IA' },
  { label: 'Humano', filter: 'TODOS', type: 'HUMANO' },
  { label: 'Meus', filter: 'MEUS', type: 'TODOS' },
  { label: 'Não lidos', filter: 'NAO_LIDOS', type: 'TODOS' },
] as const;

const moreFilters = [
  { label: 'Aguardando', filter: 'AGUARDANDO', type: 'TODOS' },
  { label: 'Convênio', filter: 'REVISAO', type: 'TODOS' },
] as const;

export function ChatList(props: Props) {
  const moreActionsTriggerRef = useRef<HTMLButtonElement>(null);
  const hasMoreActions = props.view === 'ATIVOS' && Boolean(props.canCloseAll && props.onCloseAll);

  useEffect(() => {
    props.onCloseAllTriggerReady?.(() => moreActionsTriggerRef.current?.focus());
  }, [props.onCloseAllTriggerReady]);

  return (
    <aside
      aria-label="Lista de atendimentos"
      className="flex h-full w-[352px] max-w-[calc(100vw-58px)] shrink-0 flex-col border-r border-clinic-border bg-clinic-surface"
      data-testid="chat-list"
    >
      <div className="space-y-3 border-b border-clinic-border px-4 pb-3.5 pt-4">
        <div className="flex min-h-9 items-center justify-between gap-3">
          <h1 className="min-w-0 truncate text-[19px] font-extrabold tracking-tight text-clinic-text">
            Atendimentos
          </h1>
          <div className="flex shrink-0 items-center gap-2">
            {hasMoreActions ? (
              <Menu.Root modal={false}>
                <Menu.Trigger
                  ref={moreActionsTriggerRef}
                  type="button"
                  disabled={props.closeAllLoading}
                  aria-label="Mais ações dos atendimentos"
                  title="Mais ações"
                  className="inline-flex h-9 w-9 items-center justify-center rounded-xl border border-clinic-border bg-clinic-surface text-clinic-muted shadow-sm transition hover:border-clinic-primary/30 hover:bg-clinic-hover hover:text-clinic-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-clinic-primary disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {props.closeAllLoading ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Ellipsis className="h-4 w-4" />}
                </Menu.Trigger>
                <Menu.Portal>
                  <Menu.Positioner side="bottom" align="end" sideOffset={6} className="z-[70]">
                    <Menu.Popup
                      finalFocus={moreActionsTriggerRef}
                      className="min-w-56 rounded-xl border border-clinic-border bg-clinic-surface p-1 shadow-xl outline-none"
                    >
                      <Menu.Item
                        onClick={props.onCloseAll}
                        className="flex h-9 cursor-pointer items-center gap-2 rounded-lg px-2.5 text-[11px] font-bold text-clinic-danger outline-none data-[highlighted]:bg-clinic-danger/10"
                      >
                        <CircleStop className="h-4 w-4" />
                        Encerrar todos os atendimentos
                      </Menu.Item>
                    </Menu.Popup>
                  </Menu.Positioner>
                </Menu.Portal>
              </Menu.Root>
            ) : null}
            {props.canStartManual ? (
              <button
                type="button"
                onClick={props.onStartManual}
                aria-label="Novo atendimento"
                title="Novo atendimento"
                className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-clinic-primary text-white shadow-sm transition hover:brightness-95 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-clinic-primary"
              >
                <Plus className="h-[18px] w-[18px]" />
              </button>
            ) : null}
          </div>
        </div>

        <label className="relative block" aria-busy={props.searching}>
          <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-clinic-muted" />
          <input
            type="search"
            aria-label={props.view === 'FINALIZADOS' ? 'Buscar no histórico' : 'Buscar paciente ou telefone'}
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

        <div
          role="tablist"
          aria-label="Visão dos atendimentos"
          className="grid grid-cols-2 gap-1 rounded-xl bg-clinic-soft p-1"
        >
          <button
            id="atendimentos-ativos-tab"
            type="button"
            role="tab"
            aria-selected={props.view === 'ATIVOS'}
            aria-controls="atendimentos-lista"
            onClick={() => props.onViewChange('ATIVOS')}
            className={`rounded-lg px-2 py-2 text-[11px] font-extrabold transition focus-visible:outline-2 focus-visible:outline-clinic-primary ${
              props.view === 'ATIVOS'
                ? 'bg-clinic-surface text-clinic-primary shadow-sm ring-1 ring-clinic-primary/15'
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
            className={`rounded-lg px-2 py-2 text-[11px] font-extrabold transition focus-visible:outline-2 focus-visible:outline-clinic-primary ${
              props.view === 'FINALIZADOS'
                ? 'bg-clinic-surface text-clinic-primary shadow-sm ring-1 ring-clinic-primary/15'
                : 'text-clinic-muted hover:bg-clinic-hover hover:text-clinic-text'
            }`}
          >
            Finalizados
          </button>
        </div>

        {props.view === 'FINALIZADOS' ? (
          <p className="text-[10px] font-medium text-clinic-muted">
            Histórico de atendimentos encerrados
          </p>
        ) : null}

        {props.error ? (
          <div role="alert" className="flex items-center justify-between gap-2 rounded-lg bg-clinic-danger/10 px-2.5 py-2 text-[10px] font-semibold text-clinic-danger">
            <span className="line-clamp-2">{props.error}</span>
            <button type="button" onClick={props.onRetry} className="shrink-0 font-extrabold underline">
              Tentar novamente
            </button>
          </div>
        ) : null}

        {props.view === 'ATIVOS' ? (
          <div className="-mx-1 flex items-center gap-1 overflow-x-auto px-1 pb-1 text-[10px] font-bold hide-scrollbar" aria-label="Filtros de atendimentos ativos">
            {primaryFilters.map((item) => {
              const active = props.filter === item.filter && props.type === item.type;
              return (
                <button
                  type="button"
                  key={item.label}
                  onClick={() => props.onFilterChange(item.filter, item.type)}
                  className={`shrink-0 rounded-lg border px-2.5 py-1.5 transition focus-visible:outline-2 focus-visible:outline-clinic-primary ${
                    active
                      ? 'border-clinic-primary/20 bg-clinic-soft text-clinic-primary'
                      : 'border-transparent bg-transparent text-clinic-muted hover:bg-clinic-hover hover:text-clinic-text'
                  }`}
                >
                  {item.label}
                </button>
              );
            })}
            <Menu.Root modal={false}>
              <Menu.Trigger
                type="button"
                aria-label="Mais filtros de atendimentos"
                className="inline-flex h-[30px] shrink-0 items-center gap-1 rounded-lg border border-transparent bg-transparent px-2.5 text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-text focus-visible:outline-2 focus-visible:outline-clinic-primary"
              >
                Mais
                <ChevronDown className="h-3.5 w-3.5" />
              </Menu.Trigger>
              <Menu.Portal>
                <Menu.Positioner side="bottom" align="start" sideOffset={6} className="z-[70]">
                  <Menu.Popup className="min-w-40 rounded-xl border border-clinic-border bg-clinic-surface p-1 shadow-xl outline-none">
                    {moreFilters.map((item) => {
                      const active = props.filter === item.filter && props.type === item.type;
                      return (
                        <Menu.Item
                          key={item.label}
                          onClick={() => props.onFilterChange(item.filter, item.type)}
                          className="flex h-9 cursor-pointer items-center justify-between gap-3 rounded-lg px-2.5 text-[11px] font-bold text-clinic-text outline-none data-[highlighted]:bg-clinic-hover"
                        >
                          {item.label}
                          {active ? <Check className="h-4 w-4 text-clinic-primary" /> : null}
                        </Menu.Item>
                      );
                    })}
                  </Menu.Popup>
                </Menu.Positioner>
              </Menu.Portal>
            </Menu.Root>
          </div>
        ) : null}
      </div>

      <div
        id="atendimentos-lista"
        role="tabpanel"
        aria-labelledby={props.view === 'ATIVOS' ? 'atendimentos-ativos-tab' : 'atendimentos-finalizados-tab'}
        className="flex-1 overflow-y-auto bg-clinic-surface p-2.5 custom-scrollbar"
      >
        {props.conversations.length === 0 ? (
            <div className="m-1.5 rounded-xl border border-dashed border-clinic-border bg-clinic-surface-muted px-4 py-10 text-center">
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
          const visibleTags = chat.tags.slice(0, 1);
          const hiddenTags = Math.max(chat.tags.length - visibleTags.length, 0);
          return (
            <button
              type="button"
              key={chat.id}
              onClick={() => props.onSelect(chat.id)}
              aria-current={active ? 'true' : undefined}
              className={`relative mb-1.5 block w-full overflow-hidden rounded-2xl border px-3 py-3 text-left transition last:mb-0 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-clinic-primary ${
                active
                  ? 'border-clinic-primary/25 bg-clinic-soft shadow-sm'
                  : 'border-transparent bg-clinic-surface hover:border-clinic-border hover:bg-clinic-hover'
              }`}
            >
              {active ? <span className="absolute inset-y-2 left-0 w-[3px] rounded-r-full bg-clinic-primary" /> : null}
              <div className="flex gap-3">
                <ContactAvatar name={chat.paciente.nomeBusca} url={chat.paciente.fotoUrl} variant="sidebar" />
                <div className="min-w-0 flex-1">
                  <div className="flex items-baseline justify-between gap-2">
                    <h2 className="truncate text-[13px] font-extrabold text-clinic-text">
                      {chat.paciente.nomeBusca}
                    </h2>
                    <span className="shrink-0 text-[10px] text-clinic-muted">
                      {formatTime(chat.ultimaMensagemEm)}
                    </span>
                  </div>
                  <p className="mt-0.5 truncate text-[10px] font-medium text-clinic-muted">
                    {attendanceLabel}
                  </p>
                  <p className="mt-1 truncate text-[11px] leading-4 text-clinic-muted">
                    {chat.ultimaMensagemPrevia || 'Sem mensagens'}
                  </p>
                  <div className="mt-2 flex min-w-0 flex-wrap items-center gap-1.5 text-[10px]">
                    <MessageCircle aria-hidden="true" className="h-3.5 w-3.5 shrink-0 text-clinic-success" />
                    <span className="shrink-0 rounded-full bg-clinic-blue/10 px-2 py-1 font-bold text-clinic-blue">
                      {props.view === 'FINALIZADOS' ? 'Finalizado' : chat.tratadoPorIa ? 'IA' : 'Humano'}
                    </span>
                    {visibleTags.map((tag) => (
                      <span
                        key={tag.id}
                        className="inline-flex min-w-0 max-w-[94px] items-center gap-1 rounded-full bg-clinic-soft px-2 py-1 font-bold text-clinic-text"
                      >
                        <span
                          className="h-1.5 w-1.5 shrink-0 rounded-full"
                          style={{ backgroundColor: tag.cor ?? 'var(--clinic-muted)' }}
                        />
                        <span className="truncate">{tag.nome}</span>
                      </span>
                    ))}
                    {hiddenTags > 0 ? (
                      <span className="shrink-0 rounded-full bg-clinic-soft px-1.5 py-1 font-bold text-clinic-muted">
                        +{hiddenTags}
                      </span>
                    ) : null}
                    {chat.requerRevisao ? (
                      <span className="shrink-0 rounded-full bg-clinic-warning/10 px-2 py-1 font-bold text-clinic-warning">
                        Convênio
                      </span>
                    ) : null}
                    {chat.naoLidas > 0 ? (
                      <span className="ml-auto inline-flex h-5 min-w-5 shrink-0 items-center justify-center rounded-full bg-clinic-primary px-1.5 text-center font-bold text-white">
                        {chat.naoLidas}
                      </span>
                    ) : null}
                  </div>
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
    return chat.tratadoPorIa ? 'Atendido por IA' : 'Atendimento humano';
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
