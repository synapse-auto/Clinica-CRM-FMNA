import { Filter, Search, UserPlus } from 'lucide-react';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { demoConversations } from '@/mocks/demoAtendimentos';

export function ChatList() {
  return (
    <aside className="flex h-full w-[286px] shrink-0 flex-col border-r border-clinic-border bg-clinic-surface">
      <div className="space-y-3 border-b border-clinic-border p-3">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-[16px] font-extrabold text-clinic-text">Atendimentos</h1>
            <p className="text-[9px] text-clinic-muted">Conversas e follow-ups ativos</p>
          </div>
          <div className="flex items-center gap-2 text-clinic-muted">
            <button aria-label="Novo atendimento" className="rounded-md p-1.5 transition hover:bg-clinic-hover hover:text-clinic-primary">
              <UserPlus className="h-4 w-4" />
            </button>
            <button aria-label="Filtros" className="rounded-md p-1.5 transition hover:bg-clinic-hover hover:text-clinic-primary">
              <Filter className="h-4 w-4" />
            </button>
          </div>
        </div>

        <label className="relative block">
          <Search className="absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-clinic-muted" />
          <input
            type="search"
            placeholder="Buscar lead..."
            className="h-9 w-full rounded-lg border border-clinic-border bg-clinic-input pl-9 pr-3 text-[11px] text-clinic-text outline-none transition placeholder:text-clinic-muted focus:border-clinic-primary"
          />
        </label>

        <div className="flex gap-1 overflow-x-auto text-[9px] font-bold hide-scrollbar">
          <button className="rounded-md bg-clinic-primary px-2.5 py-1.5 text-white">
            Todos <span className="opacity-80">27</span>
          </button>
          <button className="rounded-md px-2.5 py-1.5 text-clinic-muted hover:bg-clinic-hover">IA 19</button>
          <button className="rounded-md px-2.5 py-1.5 text-clinic-muted hover:bg-clinic-hover">Humano 8</button>
          <button className="rounded-md px-2.5 py-1.5 text-clinic-muted hover:bg-clinic-hover">Follow UP</button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto custom-scrollbar">
        {demoConversations.map((chat, index) => (
          <article
            key={chat.id}
            className={`relative cursor-pointer border-b border-clinic-border/70 p-3 transition hover:bg-clinic-hover ${
              index === 0 ? 'bg-clinic-soft' : ''
            }`}
          >
            {index === 0 ? <div className="absolute bottom-0 left-0 top-0 w-1 bg-clinic-primary" /> : null}
            <div className="flex gap-2.5">
              <div className="relative shrink-0">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-clinic-primary/15 text-xs font-extrabold text-clinic-primary">
                  {chat.initials}
                </div>
                <div className="absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-clinic-surface bg-clinic-success" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="mb-0.5 flex items-baseline justify-between gap-2">
                  <h2 className="truncate text-[11px] font-extrabold text-clinic-text">{chat.name}</h2>
                  <span className="shrink-0 text-[8px] text-clinic-muted">{chat.time}</span>
                </div>
                <p className="truncate text-[9px] leading-4 text-clinic-muted">{chat.preview}</p>
                <div className="mt-1.5 flex items-center gap-1">
                  {chat.tags.slice(0, 1).map((tag) => (
                    <StatusBadge key={tag} tone={tag === 'Retorno' ? 'orange' : 'blue'}>{tag}</StatusBadge>
                  ))}
                  <span className="flex h-4 w-4 items-center justify-center rounded-full bg-clinic-orange text-[8px] font-bold text-white">
                    {chat.owner[0]}
                  </span>
                  {chat.unread ? (
                    <span className="ml-auto flex h-4 min-w-4 items-center justify-center rounded-full bg-clinic-primary px-1 text-[8px] font-bold text-white">
                      {chat.unread}
                    </span>
                  ) : null}
                </div>
              </div>
            </div>
          </article>
        ))}
      </div>
    </aside>
  );
}
