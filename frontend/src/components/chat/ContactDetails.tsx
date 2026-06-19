import { Bell, Calendar, CalendarCheck, ChevronDown, Clock, Mail, Phone, StickyNote, Tag, User } from 'lucide-react';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { demoConversations, type DemoConversation } from '@/mocks/demoAtendimentos';

export function ContactDetails({
  conversation = demoConversations[0],
}: {
  conversation?: DemoConversation;
}) {
  return (
    <aside className="flex h-full w-[300px] shrink-0 flex-col overflow-y-auto border-l border-clinic-border bg-clinic-surface custom-scrollbar">
      <div className="border-b border-clinic-border p-4 text-center">
        <div className="mx-auto mb-2.5 flex h-14 w-14 items-center justify-center rounded-full bg-clinic-primary/15 text-lg font-extrabold text-clinic-primary ring-4 ring-clinic-soft">
          {conversation.initials}
        </div>
        <h2 className="text-[14px] font-extrabold leading-tight text-clinic-text">{conversation.name}</h2>
        <div className="mt-2">
          <StatusBadge tone="blue">{conversation.status}</StatusBadge>
        </div>
        {conversation.requerRevisao === true ? (
          <button
            type="button"
            className="mt-3 inline-flex h-8 items-center justify-center gap-2 rounded-lg bg-clinic-primary px-3 text-[10px] font-extrabold text-white transition hover:bg-clinic-primary-strong"
          >
            <CalendarCheck className="h-3.5 w-3.5" />
            Confirmar consulta
          </button>
        ) : null}
      </div>

      <div className="space-y-5 p-4">
        <Section title="Contato">
          <DetailRow icon={Phone} text={conversation.phone} />
          <DetailRow icon={Mail} text="paciente@email.com" />
          <DetailRow icon={User} text={conversation.owner} tone="text-clinic-orange" />
          <DetailRow icon={Clock} text="A definir" />
        </Section>

        <Section title="Tags">
          <div className="flex flex-wrap gap-1.5">
            {conversation.tags.map((tag) => (
              <span key={tag} className="inline-flex items-center gap-1 rounded-md border border-clinic-blue/20 bg-clinic-blue/10 px-2 py-1 text-[9px] font-bold text-clinic-blue">
                <Tag className="h-2.5 w-2.5" />
                {tag}
              </span>
            ))}
            <span className="inline-flex items-center gap-1 rounded-md border border-clinic-indigo/20 bg-clinic-indigo/10 px-2 py-1 text-[9px] font-bold text-clinic-indigo">
              <Tag className="h-2.5 w-2.5" />
              Novo Paciente
            </span>
          </div>
        </Section>

        <Section title="Histórico">
          <MiniMetric icon={Calendar} value="0" label="Consultas" />
        </Section>

        <Section title="Notas">
          <div className="flex gap-2 text-[10px] leading-5 text-clinic-muted">
            <StickyNote className="mt-0.5 h-3.5 w-3.5 shrink-0" />
            <p>Nova paciente, chegou via Instagram. Prefere atendimento pela manhã.</p>
          </div>
        </Section>

        <Section title="Lembretes" tone="text-clinic-orange">
          <div className="rounded-lg border border-clinic-orange/25 bg-clinic-orange/5 p-3">
            <div className="mb-2 flex gap-2">
              <button className="flex h-8 flex-1 items-center justify-between rounded-lg border border-clinic-border bg-clinic-input px-2.5 text-[10px] text-clinic-muted">
                Data <ChevronDown className="h-3.5 w-3.5" />
              </button>
              <button className="flex h-8 w-20 items-center justify-between rounded-lg border border-clinic-border bg-clinic-input px-2.5 text-[10px] text-clinic-muted">
                10:00 <Clock className="h-3 w-3" />
              </button>
            </div>
            <textarea
              placeholder="Mensagem do lembrete..."
              className="h-16 w-full resize-none rounded-lg border border-clinic-border bg-clinic-input p-2.5 text-[10px] text-clinic-text outline-none placeholder:text-clinic-muted focus:border-clinic-orange"
            />
            <button className="mt-2 flex h-8 w-full items-center justify-center gap-2 rounded-lg bg-clinic-orange text-[10px] font-extrabold text-white transition hover:opacity-90">
              <Bell className="h-3.5 w-3.5" />
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
      <h3 className={`mb-2.5 text-[9px] font-extrabold uppercase ${tone}`}>{title}</h3>
      <div className="space-y-2">{children}</div>
    </section>
  );
}

function DetailRow({ icon: Icon, text, tone = 'text-clinic-text' }: { icon: typeof Phone; text: string; tone?: string }) {
  return (
    <div className={`flex items-center gap-2.5 text-[10px] ${tone}`}>
      <Icon className="h-3.5 w-3.5 shrink-0 text-clinic-muted" />
      <span className="truncate">{text}</span>
    </div>
  );
}

function MiniMetric({ icon: Icon, value, label }: { icon: typeof Calendar; value: string; label: string }) {
  return (
    <div className="flex flex-col items-center rounded-lg border border-clinic-border bg-clinic-surface-muted p-2.5">
      <Icon className="mb-1 h-3.5 w-3.5 text-clinic-muted" />
      <span className="text-[15px] font-extrabold text-clinic-text">{value}</span>
      <span className="text-[8px] text-clinic-muted">{label}</span>
    </div>
  );
}
