import { Check, MoreVertical, Paperclip, Phone, Send, Smile } from 'lucide-react';
import { demoConversations, demoMessages } from '@/mocks/demoAtendimentos';

const activeConversation = demoConversations[0];

export function ChatWindow() {
  return (
    <section className="flex min-w-0 flex-1 flex-col bg-[#f4fbfb]">
      <div className="flex h-16 shrink-0 items-center justify-between border-b border-clinic-border bg-white px-6 shadow-sm">
        <div className="flex min-w-0 items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-teal-200 text-sm font-extrabold text-teal-800">
            {activeConversation.initials}
          </div>
          <div className="min-w-0">
            <h2 className="truncate text-base font-extrabold text-clinic-text">{activeConversation.name}</h2>
            <p className="truncate text-xs text-clinic-muted">
              {activeConversation.phone} · Atendido por{' '}
              <span className="font-bold text-orange-500">{activeConversation.owner}</span>
            </p>
          </div>
        </div>
        <div className="flex items-center gap-4 text-clinic-muted">
          <button aria-label="Ligar" className="transition hover:text-clinic-primary">
            <Phone className="h-5 w-5" />
          </button>
          <button aria-label="Mais opções" className="transition hover:text-clinic-primary">
            <MoreVertical className="h-5 w-5" />
          </button>
        </div>
      </div>

      <div className="flex-1 space-y-5 overflow-y-auto p-6 custom-scrollbar">
        {demoMessages.map((message) => {
          if (message.direction === 'system') {
            return (
              <div key={message.id} className="flex justify-center">
                <span className="rounded-full bg-slate-200/70 px-3 py-1 text-xs font-semibold text-clinic-muted">
                  {message.text}
                </span>
              </div>
            );
          }

          const outbound = message.direction === 'out';
          return (
            <div key={message.id} className={`flex flex-col ${outbound ? 'items-end' : 'items-start'}`}>
              {message.author ? <span className="mb-1 text-[10px] font-semibold text-clinic-muted">{message.author}</span> : null}
              <div
                className={`max-w-[72%] rounded-2xl px-4 py-3 text-sm leading-6 shadow-sm ${
                  outbound
                    ? 'rounded-tr-sm bg-teal-700 text-white'
                    : 'rounded-tl-sm border border-clinic-border bg-white text-clinic-text'
                }`}
              >
                {message.text}
              </div>
              <div className="mt-1 flex items-center gap-1 text-[10px] text-clinic-muted">
                {message.time}
                {outbound ? <Check className="h-3 w-3" /> : null}
              </div>
            </div>
          );
        })}
      </div>

      <div className="shrink-0 border-t border-clinic-border bg-white p-4">
        <div className="mb-3 flex flex-wrap gap-2">
          {['Confirmar consulta', 'Pedir documento', 'Enviar localização'].map((label) => (
            <button key={label} className="rounded-full border border-clinic-border px-3 py-1.5 text-xs font-bold text-clinic-muted transition hover:border-clinic-primary hover:text-clinic-primary">
              {label}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-3">
          <button aria-label="Anexar" className="text-clinic-muted transition hover:text-clinic-primary">
            <Paperclip className="h-5 w-5 -rotate-45" />
          </button>
          <div className="relative flex-1">
            <input
              placeholder="Digite uma mensagem..."
              className="h-12 w-full rounded-full border border-clinic-border bg-teal-50/35 px-5 pr-12 text-sm outline-none transition focus:border-clinic-primary focus:bg-white"
            />
            <button aria-label="Emoji" className="absolute right-4 top-1/2 -translate-y-1/2 text-clinic-muted transition hover:text-clinic-primary">
              <Smile className="h-5 w-5" />
            </button>
          </div>
          <button aria-label="Enviar" className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-clinic-primary text-white shadow-sm transition hover:bg-clinic-primary-strong">
            <Send className="ml-0.5 h-5 w-5" />
          </button>
        </div>
      </div>
    </section>
  );
}
