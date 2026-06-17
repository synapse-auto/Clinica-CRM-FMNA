import { Bell, Calendar, ChevronDown, Clock, Mail, Phone, StickyNote, Tag, User } from 'lucide-react';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { demoConversations } from '@/mocks/demoAtendimentos';

const activeConversation = demoConversations[0];

export function ContactDetails() {
  return (
    <aside className="flex h-full w-[320px] shrink-0 flex-col overflow-y-auto border-l border-clinic-border bg-white custom-scrollbar">
      <div className="border-b border-clinic-border p-6 text-center">
        <div className="mx-auto mb-4 flex h-20 w-20 items-center justify-center rounded-full bg-teal-200 text-2xl font-extrabold text-teal-800 ring-4 ring-teal-50">
          {activeConversation.initials}
        </div>
        <h2 className="text-lg font-extrabold leading-tight text-clinic-text">{activeConversation.name}</h2>
        <div className="mt-3">
          <StatusBadge tone="blue">{activeConversation.status}</StatusBadge>
        </div>
      </div>

      <div className="space-y-7 p-6">
        <Section title="Contato">
          <DetailRow icon={Phone} text={activeConversation.phone} />
          <DetailRow icon={Mail} text="paciente@email.com" />
          <DetailRow icon={User} text={activeConversation.owner} tone="text-orange-500" />
          <DetailRow icon={Clock} text="A definir" />
        </Section>

        <Section title="Tags">
          <div className="flex flex-wrap gap-2">
            {activeConversation.tags.map((tag) => (
              <span key={tag} className="inline-flex items-center gap-1.5 rounded-md border border-blue-100 bg-blue-50 px-2.5 py-1.5 text-xs font-bold text-blue-700">
                <Tag className="h-3 w-3" />
                {tag}
              </span>
            ))}
            <span className="inline-flex items-center gap-1.5 rounded-md border border-purple-100 bg-purple-50 px-2.5 py-1.5 text-xs font-bold text-purple-700">
              <Tag className="h-3 w-3" />
              Novo Paciente
            </span>
          </div>
        </Section>

        <Section title="Histórico">
          <div className="grid grid-cols-2 gap-3">
            <MiniMetric icon={Calendar} value="0" label="Consultas" />
            <MiniMetric icon={StickyNote} value="R$0" label="Total pago" />
          </div>
        </Section>

        <Section title="Notas">
          <div className="flex gap-3 text-sm leading-6 text-clinic-muted">
            <StickyNote className="mt-0.5 h-4 w-4 shrink-0" />
            <p>Nova paciente, chegou via Instagram. Prefere atendimento pela manhã.</p>
          </div>
        </Section>

        <Section title="Lembretes" tone="text-orange-500">
          <div className="rounded-xl border border-orange-200 bg-orange-50/50 p-4">
            <div className="mb-3 flex gap-2">
              <button className="flex h-9 flex-1 items-center justify-between rounded-lg border border-clinic-border bg-white px-3 text-sm text-clinic-muted">
                Data <ChevronDown className="h-4 w-4" />
              </button>
              <button className="flex h-9 w-24 items-center justify-between rounded-lg border border-clinic-border bg-white px-3 text-sm text-clinic-muted">
                10:00 <Clock className="h-3 w-3" />
              </button>
            </div>
            <textarea
              placeholder="Mensagem do lembrete..."
              className="h-20 w-full resize-none rounded-lg border border-clinic-border bg-white p-3 text-sm outline-none focus:border-orange-400"
            />
            <button className="mt-3 flex h-10 w-full items-center justify-center gap-2 rounded-lg bg-orange-300 text-sm font-extrabold text-orange-950 transition hover:bg-orange-400">
              <Bell className="h-4 w-4" />
              Adicionar lembrete
            </button>
          </div>
        </Section>
      </div>
    </aside>
  );
}

type SectionProps = {
  title: string;
  tone?: string;
  children: React.ReactNode;
};

function Section({ title, tone = 'text-clinic-muted', children }: SectionProps) {
  return (
    <section>
      <h3 className={`mb-4 text-[11px] font-extrabold uppercase tracking-[0.18em] ${tone}`}>{title}</h3>
      <div className="space-y-3">{children}</div>
    </section>
  );
}

function DetailRow({ icon: Icon, text, tone = 'text-clinic-text' }: { icon: typeof Phone; text: string; tone?: string }) {
  return (
    <div className={`flex items-center gap-3 text-sm ${tone}`}>
      <Icon className="h-4 w-4 shrink-0 text-clinic-muted" />
      <span className="truncate">{text}</span>
    </div>
  );
}

function MiniMetric({ icon: Icon, value, label }: { icon: typeof Calendar; value: string; label: string }) {
  return (
    <div className="flex flex-col items-center rounded-xl border border-clinic-border bg-white p-3 shadow-sm">
      <Icon className="mb-1 h-4 w-4 text-clinic-muted" />
      <span className="text-xl font-extrabold text-clinic-text">{value}</span>
      <span className="text-[10px] text-clinic-muted">{label}</span>
    </div>
  );
}
