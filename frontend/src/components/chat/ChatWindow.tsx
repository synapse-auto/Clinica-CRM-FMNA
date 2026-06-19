import { CheckCheck, MoreVertical, Paperclip, Phone, Send, Smile } from 'lucide-react';
import { demoConversations, demoMessages } from '@/mocks/demoAtendimentos';

const activeConversation = demoConversations[0];

export function ChatWindow() {
  return (
    <section className="flex min-w-0 flex-1 flex-col bg-clinic-surface-muted">
      <div className="flex h-[58px] shrink-0 items-center justify-between border-b border-clinic-border bg-clinic-surface px-4">
        <div className="flex min-w-0 items-center gap-2.5">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-clinic-primary/15 text-xs font-extrabold text-clinic-primary">
            {activeConversation.initials}
          </div>
          <div className="min-w-0">
            <h2 className="truncate text-[13px] font-extrabold text-clinic-text">{activeConversation.name}</h2>
            <p className="truncate text-[9px] text-clinic-muted">
              {activeConversation.phone} · Atendido por{' '}
              <span className="font-bold text-clinic-orange">{activeConversation.owner}</span>
            </p>
          </div>
        </div>
        <div className="flex items-center gap-1 text-clinic-muted">
          <button aria-label="Ligar" className="rounded-lg p-2 transition hover:bg-clinic-hover hover:text-clinic-primary">
            <Phone className="h-4 w-4" />
          </button>
          <button aria-label="Mais opções" className="rounded-lg p-2 transition hover:bg-clinic-hover hover:text-clinic-primary">
            <MoreVertical className="h-4 w-4" />
          </button>
        </div>
      </div>

      <div className="flex-1 space-y-4 overflow-y-auto p-5 custom-scrollbar">
        {demoMessages.map((message) => {
          if (message.direction === 'system') {
            return (
              <div key={message.id} className="flex justify-center">
                <span className="rounded-full border border-clinic-border bg-clinic-soft px-3 py-1 text-[9px] font-semibold text-clinic-muted">
                  {message.text}
                </span>
              </div>
            );
          }

          const outbound = message.direction === 'out';
          return (
            <div key={message.id} className={`flex flex-col ${outbound ? 'items-end' : 'items-start'}`}>
              {message.author ? <span className="mb-1 text-[8px] font-semibold text-clinic-muted">{message.author}</span> : null}
              <div
                className={`max-w-[72%] rounded-xl px-3.5 py-2.5 text-[11px] leading-5 shadow-sm ${
                  outbound
                    ? 'rounded-tr-sm bg-clinic-primary text-white'
                    : 'rounded-tl-sm border border-clinic-border bg-clinic-surface text-clinic-text'
                }`}
              >
                {message.text}
              </div>
              <div className="mt-1 flex items-center gap-1 text-[8px] text-clinic-muted">
                {message.time}
                {outbound ? <CheckCheck className="h-3 w-3 text-clinic-primary" /> : null}
              </div>
            </div>
          );
        })}
      </div>

      <div className="shrink-0 border-t border-clinic-border bg-clinic-surface p-3">
        <div className="flex items-center gap-2">
          <button aria-label="Anexar" className="rounded-full p-2 text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-primary">
            <Paperclip className="h-4 w-4 -rotate-45" />
          </button>
          <div className="relative flex-1">
            <input
              placeholder="Digite uma mensagem..."
              className="h-10 w-full rounded-full border border-clinic-border bg-clinic-input px-4 pr-10 text-[11px] text-clinic-text outline-none transition placeholder:text-clinic-muted focus:border-clinic-primary"
            />
            <button aria-label="Emoji" className="absolute right-3 top-1/2 -translate-y-1/2 text-clinic-muted transition hover:text-clinic-primary">
              <Smile className="h-4 w-4" />
            </button>
          </div>
          <button aria-label="Enviar" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-clinic-primary text-white transition hover:bg-clinic-primary-strong">
            <Send className="ml-0.5 h-4 w-4" />
          </button>
        </div>
      </div>
    </section>
  );
}
