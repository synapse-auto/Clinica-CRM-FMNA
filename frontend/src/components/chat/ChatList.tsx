'use client';

import { Search } from 'lucide-react';
import type {
  AtendimentoFilter,
  AtendimentoResumo,
} from '@/types/atendimento';

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
    <aside className="flex h-full w-[300px] shrink-0 flex-col border-r border-clinic-border bg-clinic-surface">
      <div className="space-y-3 border-b border-clinic-border p-3">
        <div>
          <h1 className="text-[16px] font-extrabold text-clinic-text">Atendimentos</h1>
          <p className="text-[9px] text-clinic-muted">Conversas reais do WhatsApp</p>
        </div>
        <label className="relative block">
          <Search className="absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-clinic-muted" />
          <input
            type="search"
            value={props.search}
            onChange={(event) => props.onSearchChange(event.target.value)}
            placeholder="Buscar paciente ou telefone..."
            className="h-9 w-full rounded-lg border border-clinic-border bg-clinic-input pl-9 pr-3 text-[11px] text-clinic-text outline-none focus:border-clinic-primary"
          />
        </label>
        <div className="flex gap-1 overflow-x-auto text-[9px] font-bold hide-scrollbar">
          {filters.map((item) => {
            const active = props.filter === item.filter && props.type === item.type;
            return (
              <button
                key={item.label}
                onClick={() => props.onFilterChange(item.filter, item.type)}
                className={`shrink-0 rounded-md px-2.5 py-1.5 ${
                  active
                    ? 'bg-clinic-primary text-white'
                    : 'text-clinic-muted hover:bg-clinic-hover'
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
          <p className="p-5 text-center text-[11px] text-clinic-muted">
            Nenhum atendimento encontrado.
          </p>
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
              className={`relative block w-full border-b border-clinic-border/70 p-3 text-left transition hover:bg-clinic-hover ${
                active ? 'bg-clinic-soft' : ''
              }`}
            >
              {active ? <span className="absolute inset-y-0 left-0 w-1 bg-clinic-primary" /> : null}
              <div className="flex gap-2.5">
                <Avatar name={chat.paciente.nomeBusca} url={chat.paciente.fotoUrl} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-baseline justify-between gap-2">
                    <h2 className="truncate text-[11px] font-extrabold text-clinic-text">
                      {chat.paciente.nomeBusca}
                    </h2>
                    <span className="shrink-0 text-[8px] text-clinic-muted">
                      {formatTime(chat.ultimaMensagemEm)}
                    </span>
                  </div>
                  <p className="truncate text-[9px] leading-4 text-clinic-muted">
                    {chat.ultimaMensagemPrevia || 'Sem mensagens'}
                  </p>
                  <p className="mt-0.5 truncate text-[8px] font-semibold text-clinic-muted">
                    {attendanceLabel}
                  </p>
                  <div className="mt-1.5 flex flex-wrap items-center gap-1 text-[8px]">
                    <span className="rounded bg-clinic-blue/10 px-1.5 py-0.5 font-bold text-clinic-blue">
                      {chat.tratadoPorIa ? 'IA' : 'Humano'}
                    </span>
                    {chat.requerRevisao ? (
                      <span className="rounded bg-clinic-warning/10 px-1.5 py-0.5 font-bold text-clinic-warning">
                        Convênio
                      </span>
                    ) : null}
                    {chat.naoLidas > 0 ? (
                      <span className="ml-auto min-w-4 rounded-full bg-clinic-primary px-1 py-0.5 text-center font-bold text-white">
                        {chat.naoLidas}
                      </span>
                    ) : null}
                  </div>
                  {visibleTags.length > 0 ? (
                    <div className="mt-1.5 flex max-w-full flex-wrap gap-1 overflow-hidden">
                      {visibleTags.map((tag) => (
                        <span
                          key={tag.id}
                          className="inline-flex max-w-[92px] items-center gap-1 rounded-full border border-clinic-border bg-clinic-soft px-1.5 py-0.5 text-[8px] font-bold text-clinic-text"
                        >
                          <span
                            className="h-1.5 w-1.5 shrink-0 rounded-full"
                            style={{ backgroundColor: tag.cor ?? '#64748b' }}
                          />
                          <span className="truncate">{tag.nome}</span>
                        </span>
                      ))}
                      {hiddenTags > 0 ? (
                        <span className="rounded-full border border-clinic-border bg-clinic-soft px-1.5 py-0.5 text-[8px] font-bold text-clinic-muted">
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

function Avatar({ name, url }: { name: string; url?: string | null }) {
  const initials = name.split(/\s+/).slice(0, 2).map((part) => part[0]).join('');
  return (
    <div className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-clinic-primary/15 text-xs font-extrabold text-clinic-primary">
      {url ? (
        <img
          src={url}
          alt={name}
          className="absolute inset-0 h-full w-full rounded-full object-cover"
          onError={(e) => {
            e.currentTarget.style.display = 'none';
            if (e.currentTarget.nextElementSibling) {
              (e.currentTarget.nextElementSibling as HTMLElement).style.display = 'block';
            }
          }}
        />
      ) : null}
      <span style={{ display: url ? 'none' : 'block' }}>{initials}</span>
    </div>
  );
}

function formatTime(value: string | null) {
  if (!value) return '';
  return new Intl.DateTimeFormat('pt-BR', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}
