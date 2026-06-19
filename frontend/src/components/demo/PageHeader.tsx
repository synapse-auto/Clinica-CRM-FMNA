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
    <header className="mb-3 flex min-h-[49px] flex-col gap-3 border-b border-clinic-border pb-3 md:flex-row md:items-center md:justify-between">
      <div className="flex min-w-0 items-center gap-3">
        {icon ? (
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-clinic-soft text-clinic-primary">
            {icon}
          </div>
        ) : null}
        <div className="min-w-0">
          {eyebrow ? <p className="mb-0.5 text-[9px] font-semibold uppercase text-clinic-muted">{eyebrow}</p> : null}
          <h1 className="truncate text-[17px] font-extrabold leading-6 text-clinic-text">{title}</h1>
          {description ? <p className="text-[10px] leading-4 text-clinic-muted">{description}</p> : null}
        </div>
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
    </header>
  );
}
