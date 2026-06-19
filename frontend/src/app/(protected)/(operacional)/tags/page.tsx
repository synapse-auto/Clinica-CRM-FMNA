import { ChevronDown, Eye, Plus, Tag } from 'lucide-react';
import { AnimatedProgress } from '@/components/demo/AnimatedProgress';
import { PageHeader } from '@/components/demo/PageHeader';
import { demoTags, type DemoTag } from '@/mocks/demoOperacional';

const tagTones: Record<
  DemoTag['tone'],
  { icon: string; dot: string; progress: string }
> = {
  blue: {
    icon: 'bg-clinic-blue/15 text-clinic-blue',
    dot: 'bg-clinic-blue',
    progress: 'bg-clinic-blue',
  },
  red: {
    icon: 'bg-clinic-danger/15 text-clinic-danger',
    dot: 'bg-clinic-danger',
    progress: 'bg-clinic-danger',
  },
  orange: {
    icon: 'bg-clinic-orange/15 text-clinic-orange',
    dot: 'bg-clinic-orange',
    progress: 'bg-clinic-orange',
  },
  green: {
    icon: 'bg-clinic-success/15 text-clinic-success',
    dot: 'bg-clinic-success',
    progress: 'bg-clinic-success',
  },
  purple: {
    icon: 'bg-clinic-indigo/15 text-clinic-indigo',
    dot: 'bg-clinic-indigo',
    progress: 'bg-clinic-indigo',
  },
  pink: {
    icon: 'bg-clinic-pink/15 text-clinic-pink',
    dot: 'bg-clinic-pink',
    progress: 'bg-clinic-pink',
  },
  teal: {
    icon: 'bg-clinic-primary/15 text-clinic-primary',
    dot: 'bg-clinic-primary',
    progress: 'bg-clinic-primary',
  },
  yellow: {
    icon: 'bg-clinic-orange/15 text-clinic-orange',
    dot: 'bg-clinic-orange',
    progress: 'bg-clinic-orange',
  },
};

export default function TagsPage() {
  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <PageHeader
        title="Tags"
        description="Gerencie as etiquetas para organizar seus leads"
        actions={
          <>
            <button
              type="button"
              className="flex h-8 items-center gap-2 rounded-lg border border-clinic-border bg-clinic-surface px-3 text-[10px] font-bold text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-text"
            >
              <Eye className="h-3.5 w-3.5" />
              Expandir Tudo
            </button>
            <button
              type="button"
              className="flex h-8 items-center gap-2 rounded-lg bg-clinic-primary px-3 text-[10px] font-bold text-white transition hover:bg-clinic-primary-strong"
            >
              <Plus className="h-3.5 w-3.5" />
              Nova Tag
            </button>
          </>
        }
      />

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
        {demoTags.map((tag) => (
          <TagCard key={tag.id} tag={tag} />
        ))}
      </div>
    </div>
  );
}

function TagCard({ tag }: { tag: DemoTag }) {
  const tone = tagTones[tag.tone];
  const leadLabel = tag.uso === 1 ? 'lead' : 'leads';

  return (
    <article className="min-h-[154px] rounded-xl border border-clinic-border bg-clinic-surface p-4 shadow-[0_1px_2px_rgba(4,32,36,0.04)]">
      <div className="flex items-start justify-between">
        <span className={`flex h-10 w-10 items-center justify-center rounded-xl ${tone.icon}`}>
          <Tag className="h-5 w-5" />
        </span>
        <button
          type="button"
          aria-label={`Expandir tag ${tag.nome}`}
          className="rounded-md p-1 text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-text"
        >
          <ChevronDown className="h-3.5 w-3.5" />
        </button>
      </div>

      <h2 className="mt-3 text-[11px] font-extrabold text-clinic-text">{tag.nome}</h2>
      <p className="mt-2 flex items-center gap-2 text-[9px] text-clinic-muted">
        <span className={`h-2 w-2 rounded-full ${tone.dot}`} />
        {tag.uso} {leadLabel}
      </p>

      <div className="mt-3 h-1 rounded-full bg-clinic-soft">
        <AnimatedProgress
          value={tag.percentual}
          label={`Uso da tag ${tag.nome}`}
          className={tone.progress}
        />
      </div>
      <p className="mt-1.5 text-[8px] text-clinic-muted">{tag.percentual}% dos leads</p>
    </article>
  );
}
