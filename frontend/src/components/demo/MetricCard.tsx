import type { LucideIcon } from 'lucide-react';
import { TrendingDown, TrendingUp } from 'lucide-react';

type MetricCardProps = {
  title: string;
  value: string | number;
  subtitle: string;
  trend?: string;
  trendDirection?: 'up' | 'down';
  icon: LucideIcon;
  tone?: 'teal' | 'blue' | 'purple' | 'orange' | 'cyan';
};

const tones = {
  teal: 'bg-clinic-primary/10 text-clinic-primary',
  blue: 'bg-clinic-blue/10 text-clinic-blue',
  purple: 'bg-clinic-indigo/10 text-clinic-indigo',
  orange: 'bg-clinic-orange/10 text-clinic-orange',
  cyan: 'bg-clinic-cyan/10 text-clinic-cyan',
};

export function MetricCard({
  title,
  value,
  subtitle,
  trend,
  trendDirection = 'up',
  icon: Icon,
  tone = 'teal',
}: MetricCardProps) {
  const TrendIcon = trendDirection === 'up' ? TrendingUp : TrendingDown;
  const trendTone = trendDirection === 'up' ? 'text-clinic-success' : 'text-clinic-warning';

  return (
    <section className="min-h-[96px] rounded-xl border border-clinic-border bg-clinic-surface p-3 shadow-[0_1px_2px_rgba(4,32,36,0.04)]">
      <div className="mb-2 flex items-start justify-between gap-2">
        <div className={`flex h-7 w-7 items-center justify-center rounded-lg ${tones[tone]}`}>
          <Icon className="h-4 w-4" />
        </div>
        {trend ? (
          <div className={`flex min-w-0 items-center gap-1 text-[8px] font-semibold ${trendTone}`}>
            <TrendIcon className="h-2.5 w-2.5 shrink-0" />
            <span className="truncate">{trend}</span>
          </div>
        ) : null}
      </div>
      <p className="text-[20px] font-extrabold leading-none text-clinic-text">{value}</p>
      <p className="mt-1.5 truncate text-[10px] font-bold text-clinic-text">{title}</p>
      <p className="truncate text-[8px] text-clinic-muted">{subtitle}</p>
    </section>
  );
}
