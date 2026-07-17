import Link from 'next/link';
import { CalendarDays } from 'lucide-react';
import type { DashboardFilter } from '@/lib/dashboard-period';
import { getDashboardPeriod } from '@/lib/dashboard-period';

const options: Array<{ value: DashboardFilter; label: string }> = [
  { value: 'SEMANA_ATUAL', label: 'Semana atual' },
  { value: 'MES_ATUAL', label: 'Mês atual' },
  { value: 'MES_ANTERIOR', label: 'Mês anterior' },
];

export function DashboardPeriodFilter({ selected, today }: { selected: DashboardFilter; today: string }) {
  return (
    <nav aria-label="Período do Dashboard" className="inline-flex items-center gap-1 rounded-lg border border-clinic-border bg-clinic-surface-muted p-1">
      <CalendarDays className="ml-1 h-3.5 w-3.5 text-clinic-muted" aria-hidden="true" />
      {options.map((option) => {
        const period = getDashboardPeriod(option.value, today);
        const href = `/dashboard?periodo=${period.backendPeriod}&data=${period.data}`;
        const active = selected === option.value;
        return (
          <Link
            key={option.value}
            href={href}
            aria-current={active ? 'page' : undefined}
            aria-label={`${option.label}${active ? ', selecionado' : ''}`}
            data-state={active ? 'on' : 'off'}
            className={active
              ? 'flex h-7 items-center rounded-md bg-clinic-primary px-2.5 text-[10px] font-bold text-white shadow-sm'
              : 'flex h-7 items-center rounded-md px-2.5 text-[10px] font-semibold text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-clinic-primary/40'}
          >
            {option.label}
          </Link>
        );
      })}
    </nav>
  );
}
