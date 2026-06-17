import type { ReactNode } from 'react';

type DemoCardProps = {
  title?: string;
  description?: string;
  icon?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
};

export function DemoCard({ title, description, icon, actions, children, className = '' }: DemoCardProps) {
  return (
    <section className={`rounded-xl border border-clinic-border bg-white shadow-sm ${className}`}>
      {(title || description || icon || actions) && (
        <div className="flex items-start justify-between gap-4 border-b border-clinic-border/60 px-5 py-4">
          <div className="flex min-w-0 items-start gap-3">
            {icon ? (
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-teal-50 text-clinic-primary">
                {icon}
              </div>
            ) : null}
            <div className="min-w-0">
              {title ? <h2 className="font-bold text-clinic-text">{title}</h2> : null}
              {description ? <p className="mt-0.5 text-sm text-clinic-muted">{description}</p> : null}
            </div>
          </div>
          {actions ? <div className="shrink-0">{actions}</div> : null}
        </div>
      )}
      {children}
    </section>
  );
}
