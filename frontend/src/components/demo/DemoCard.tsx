import type { ReactNode } from 'react';

type DemoCardProps = {
  title?: string;
  description?: string;
  icon?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
};

export function DemoCard({
  title,
  description,
  icon,
  actions,
  children,
  className = '',
  bodyClassName = '',
}: DemoCardProps) {
  return (
    <section className={`overflow-hidden rounded-xl border border-clinic-border bg-clinic-surface shadow-[0_1px_2px_rgba(4,32,36,0.04)] ${className}`}>
      {(title || description || icon || actions) && (
        <div className="flex min-h-[58px] items-start justify-between gap-3 px-4 pb-2.5 pt-3.5">
          <div className="flex min-w-0 items-start gap-3">
            {icon ? (
              <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-clinic-soft text-clinic-primary">
                {icon}
              </div>
            ) : null}
            <div className="min-w-0">
              {title ? <h2 className="text-[15px] font-bold leading-6 text-clinic-text">{title}</h2> : null}
              {description ? <p className="text-xs leading-5 text-clinic-muted">{description}</p> : null}
            </div>
          </div>
          {actions ? <div className="shrink-0 text-clinic-primary">{actions}</div> : null}
        </div>
      )}
      <div className={bodyClassName}>{children}</div>
    </section>
  );
}
