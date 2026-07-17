import type { DashboardPeriodSelection } from '@/lib/dashboard-period';

export function DashboardPeriodLabel({ period }: { period: DashboardPeriodSelection }) {
  return (
    <div
      aria-label={`Período exibido: ${period.title}, ${period.value}`}
      title={`Período exibido: ${period.title}, ${period.value}`}
      className="mb-3 flex min-h-12 items-center justify-center rounded-xl border border-clinic-border bg-clinic-surface px-4 py-2 text-center"
    >
      <div>
        <p className="text-[9px] font-extrabold uppercase tracking-[0.14em] text-clinic-muted">{period.title}</p>
        <p className="mt-1 text-[13px] font-extrabold text-clinic-text">{period.value}</p>
      </div>
    </div>
  );
}
