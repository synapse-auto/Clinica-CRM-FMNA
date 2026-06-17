import type { LucideIcon } from 'lucide-react';
import { TrendingUp } from 'lucide-react';

type MetricCardProps = {
  title: string;
  value: string | number;
  subtitle: string;
  trend?: string;
  icon: LucideIcon;
  tone?: 'teal' | 'blue' | 'purple' | 'orange' | 'cyan';
};

const tones = {
  teal: 'bg-teal-50 text-teal-600',
  blue: 'bg-blue-50 text-blue-600',
  purple: 'bg-purple-50 text-purple-600',
  orange: 'bg-orange-50 text-orange-500',
  cyan: 'bg-cyan-50 text-cyan-600',
};

export function MetricCard({ title, value, subtitle, trend, icon: Icon, tone = 'teal' }: MetricCardProps) {
  return (
    <section className="min-h-[140px] rounded-xl border border-clinic-border bg-white p-5 shadow-sm">
      <div className="mb-4 flex items-start justify-between gap-3">
        <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${tones[tone]}`}>
          <Icon className="h-5 w-5" />
        </div>
        {trend ? (
          <div className="flex min-w-0 items-center gap-1 text-[11px] font-semibold text-clinic-primary">
            <TrendingUp className="h-3 w-3 shrink-0" />
            <span className="truncate">{trend}</span>
          </div>
        ) : null}
      </div>
      <p className="text-3xl font-extrabold leading-none text-clinic-text">{value}</p>
      <p className="mt-2 text-sm font-bold text-clinic-text">{title}</p>
      <p className="mt-1 text-xs text-clinic-muted">{subtitle}</p>
    </section>
  );
}
