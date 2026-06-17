import { Filter, Search, UserPlus } from 'lucide-react';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { demoConversations } from '@/mocks/demoAtendimentos';

export function ChatList() {
  return (
    <aside className="flex h-full w-[290px] shrink-0 flex-col border-r border-clinic-border bg-white/70">
      <div className="space-y-4 border-b border-clinic-border p-4">
        <div className="flex items-center justify-between">
          <h1 className="text-lg font-extrabold text-clinic-text">Atendimentos</h1>
          <div className="flex items-center gap-3 text-clinic-muted">
            <button aria-label="Novo atendimento" className="transition hover:text-clinic-primary">
              <UserPlus className="h-4 w-4" />
            </button>
            <button aria-label="Filtros" className="transition hover:text-clinic-primary">
              <Filter className="h-4 w-4" />
            </button>
          </div>
        </div>

        <label className="relative block">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-clinic-muted" />
          <input
            type="search"
            placeholder="Buscar lead..."
            className="h-10 w-full rounded-lg border border-clinic-border bg-teal-50/40 pl-9 pr-3 text-sm outline-none transition focus:border-clinic-primary focus:bg-white"
          />
        </label>

        <div className="flex gap-2 overflow-x-auto pb-1 text-xs font-bold hide-scrollbar">
          <button className="rounded-lg bg-clinic-primary px-3 py-2 text-white">
            Todos <span className="opacity-80">27</span>
          </button>
          <button className="rounded-lg px-3 py-2 text-clinic-muted hover:bg-teal-50">IA 19</button>
          <button className="rounded-lg px-3 py-2 text-clinic-muted hover:bg-teal-50">Humano 8</button>
          <button className="rounded-lg px-3 py-2 text-clinic-muted hover:bg-teal-50">Follow UP</button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto custom-scrollbar">
        {demoConversations.map((chat, index) => (
          <article
            key={chat.id}
            className={`relative cursor-pointer border-b border-clinic-border/70 p-4 transition hover:bg-teal-50/45 ${
              index === 0 ? 'bg-teal-50/55' : ''
            }`}
          >
            {index === 0 ? <div className="absolute bottom-0 left-0 top-0 w-1 bg-clinic-primary" /> : null}
            <div className="flex gap-3">
              <div className="relative shrink-0">
                <div className="flex h-11 w-11 items-center justify-center rounded-full bg-teal-200 text-sm font-extrabold text-teal-800">
                  {chat.initials}
                </div>
                <div className="absolute bottom-0 right-0 h-3.5 w-3.5 rounded-full border-2 border-white bg-emerald-500" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="mb-1 flex items-baseline justify-between gap-2">
                  <h2 className="truncate text-sm font-extrabold text-clinic-text">{chat.name}</h2>
                  <span className="shrink-0 text-[10px] text-clinic-muted">{chat.time}</span>
                </div>
                <p className="truncate text-xs leading-5 text-clinic-muted">{chat.preview}</p>
                <div className="mt-2 flex items-center gap-1.5">
                  {chat.tags.slice(0, 1).map((tag) => (
                    <StatusBadge key={tag} tone={tag === 'Retorno' ? 'orange' : 'blue'}>{tag}</StatusBadge>
                  ))}
                  <span className="flex h-5 w-5 items-center justify-center rounded-full bg-orange-400 text-[10px] font-bold text-white">
                    {chat.owner[0]}
                  </span>
                  {chat.unread ? (
                    <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-clinic-primary px-1 text-[10px] font-bold text-white">
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
