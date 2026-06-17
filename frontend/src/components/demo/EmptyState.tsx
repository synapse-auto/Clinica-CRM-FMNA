import type { LucideIcon } from 'lucide-react';

type EmptyStateProps = {
  icon: LucideIcon;
  title: string;
  description: string;
};

export function EmptyState({ icon: Icon, title, description }: EmptyStateProps) {
  return (
    <div className="flex min-h-[320px] flex-col items-center justify-center rounded-xl border border-dashed border-clinic-border bg-white/45 p-8 text-center">
      <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-teal-100 text-clinic-primary">
        <Icon className="h-8 w-8" />
      </div>
      <h2 className="text-lg font-extrabold text-clinic-text">{title}</h2>
      <p className="mt-2 max-w-md text-sm leading-6 text-clinic-muted">{description}</p>
    </div>
  );
}
