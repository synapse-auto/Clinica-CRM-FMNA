'use client';

import { Search } from 'lucide-react';
import type {
  AtendimentoFilter,
  AtendimentoResumo,
} from '@/types/atendimento';
import { ContactAvatar } from './ContactAvatar';

type Props = {
  conversations: AtendimentoResumo[];
  activeId: number | null;
  filter: AtendimentoFilter;
  type: 'TODOS' | 'IA' | 'HUMANO';
  search: string;
  onSelect: (id: number) => void;
  onFilterChange: (
    filter: AtendimentoFilter,
    type: 'TODOS' | 'IA' | 'HUMANO',
  ) => void;
  onSearchChange: (value: string) => void;
};

const filters = [
  { label: 'Todos', filter: 'TODOS', type: 'TODOS' },
  { label: 'IA', filter: 'TODOS', type: 'IA' },
  { label: 'Humano', filter: 'TODOS', type: 'HUMANO' },
  { label: 'Meus', filter: 'MEUS', type: 'TODOS' },
  { label: 'Não lidos', filter: 'NAO_LIDOS', type: 'TODOS' },
  { label: 'Aguardando', filter: 'AGUARDANDO', type: 'TODOS' },
  { label: 'Finalizados', filter: 'FINALIZADOS', type: 'TODOS' },
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
          <span className="rounded-full border border-clinic-border bg-clinic-soft px-2 py-1 text-[10px] font-bold text-clinic-primary">
            Ao vivo
          </span>
        </div>
        <label className="relative block">
          <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-clinic-muted" />
          <input
            type="search"
            value={props.search}
            onChange={(event) => props.onSearchChange(event.target.value)}
            placeholder="Buscar paciente ou telefone..."
            className="h-10 w-full rounded-xl border border-clinic-border bg-clinic-input pl-10 pr-3 text-[12px] text-clinic-text outline-none transition placeholder:text-clinic-muted focus:border-clinic-primary focus:ring-4 focus:ring-clinic-primary/10"
          />
        </label>
        <div className="flex gap-1.5 overflow-x-auto text-[10px] font-bold hide-scrollbar" aria-label="Filtros de atendimentos">
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
      </div>

      <div className="flex-1 overflow-y-auto custom-scrollbar">
        {props.conversations.length === 0 ? (
            <div className="m-4 rounded-xl border border-dashed border-clinic-border bg-clinic-surface-muted px-4 py-10 text-center">
              <p className="text-[12px] font-bold text-clinic-text">Nenhum atendimento encontrado.</p>
              <p className="mt-1 text-[11px] text-clinic-muted">Ajuste a busca ou selecione outro filtro.</p>
            </div>
        ) : props.conversations.map((chat) => {
          const active = chat.id === props.activeId;
          const attendanceLabel = getAttendanceLabel(chat);
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
                      {chat.tratadoPorIa ? 'IA' : 'Humano'}
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

function getAttendanceLabel(chat: AtendimentoResumo) {
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
