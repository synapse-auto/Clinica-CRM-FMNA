import type { ReactNode } from 'react';

type PageHeaderProps = {
  eyebrow?: string;
  title: string;
  description?: string;
  icon?: ReactNode;
  actions?: ReactNode;
};

export function PageHeader({ eyebrow, title, description, icon, actions }: PageHeaderProps) {
  return (
    <header className="mb-6 flex flex-col gap-4 border-b border-clinic-border/80 pb-6 md:flex-row md:items-center md:justify-between">
      <div className="flex min-w-0 items-center gap-4">
        {icon ? (
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-teal-100 text-clinic-primary">
            {icon}
          </div>
        ) : null}
        <div className="min-w-0">
          {eyebrow ? <p className="mb-1 text-xs font-semibold uppercase tracking-[0.18em] text-clinic-muted">{eyebrow}</p> : null}
          <h1 className="truncate text-2xl font-extrabold tracking-tight text-clinic-text">{title}</h1>
          {description ? <p className="mt-1 text-sm text-clinic-muted">{description}</p> : null}
        </div>
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
    </header>
  );
}
