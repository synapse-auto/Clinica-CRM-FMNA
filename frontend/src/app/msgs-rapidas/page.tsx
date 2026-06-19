import { Copy, Plus, Zap } from 'lucide-react';
import { PageHeader } from '@/components/demo/PageHeader';
import {
  demoMensagensRapidas,
  type DemoMensagemRapida,
} from '@/mocks/demoOperacional';

const categories = [
  'Todas',
  ...Array.from(new Set(demoMensagensRapidas.map((message) => message.categoria))),
];

const messageTones = {
  blue: {
    icon: 'bg-clinic-blue/10 text-clinic-blue',
    badge: 'bg-clinic-blue/10 text-clinic-blue',
  },
  teal: {
    icon: 'bg-clinic-primary/10 text-clinic-primary',
    badge: 'bg-clinic-primary/10 text-clinic-primary',
  },
  purple: {
    icon: 'bg-clinic-indigo/10 text-clinic-indigo',
    badge: 'bg-clinic-indigo/10 text-clinic-indigo',
  },
  pink: {
    icon: 'bg-clinic-pink/10 text-clinic-pink',
    badge: 'bg-clinic-pink/10 text-clinic-pink',
  },
};

export default function MensagensRapidasPage() {
  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <PageHeader
        title="Mensagens Rápidas"
        description="Templates prontos para agilizar atendimentos"
        actions={
          <button
            type="button"
            className="flex h-8 items-center gap-2 rounded-lg bg-clinic-primary px-3 text-[10px] font-bold text-white transition hover:bg-clinic-primary-strong"
          >
            <Plus className="h-3.5 w-3.5" />
            Nova Mensagem
          </button>
        }
      />

      <div className="mb-4 flex flex-wrap gap-2">
        {categories.map((category, index) => (
          <button
            key={category}
            type="button"
            className={
              index === 0
                ? 'rounded-full bg-clinic-primary px-3 py-1.5 text-[9px] font-bold text-white'
                : 'rounded-full bg-clinic-soft px-3 py-1.5 text-[9px] font-semibold text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-text'
            }
          >
            {category}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-3 lg:grid-cols-2 xl:grid-cols-3">
        {demoMensagensRapidas.map((message) => (
          <MessageCard key={message.id} message={message} />
        ))}
      </div>
    </div>
  );
}

function MessageCard({ message }: { message: DemoMensagemRapida }) {
  const tone = messageTones[message.tone];

  return (
    <article className="flex min-h-[194px] flex-col rounded-xl border border-clinic-border bg-clinic-surface p-4 shadow-[0_1px_2px_rgba(4,32,36,0.04)]">
      <div className="flex items-start gap-2.5">
        <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-xl ${tone.icon}`}>
          <Zap className="h-4 w-4" />
        </span>
        <div className="min-w-0">
          <h2 className="truncate text-[11px] font-extrabold text-clinic-text">{message.titulo}</h2>
          <span className={`mt-1 inline-flex rounded-full px-2 py-0.5 text-[8px] font-bold ${tone.badge}`}>
            {message.categoria}
          </span>
        </div>
      </div>

      <p className="mt-3 flex-1 text-[10px] leading-5 text-clinic-text">{message.texto}</p>

      <button
        type="button"
        className="mt-3 flex h-9 w-full items-center justify-center gap-2 rounded-lg border border-clinic-border bg-clinic-surface-muted text-[9px] font-semibold text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-text"
      >
        <Copy className="h-3.5 w-3.5" />
        Copiar mensagem
      </button>
    </article>
  );
}
