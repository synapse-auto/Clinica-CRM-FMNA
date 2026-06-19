'use client';

import { useState, type CSSProperties } from 'react';

type DonutItem = {
  label: string;
  value: number;
  color: string;
};

export function DonutChart({
  items,
  centerLabel,
  compact = false,
  valueMode = 'percentage',
}: {
  items: DonutItem[];
  centerLabel?: string;
  compact?: boolean;
  valueMode?: 'percentage' | 'value';
}) {
  const [activeIndex, setActiveIndex] = useState<number | null>(null);
  const total = Math.max(items.reduce((sum, item) => sum + item.value, 0), 1);
  const radius = 42;
  const circumference = 2 * Math.PI * radius;
  const segments = buildSegments(items, total, circumference);
  const activeItem = activeIndex === null ? null : items[activeIndex];
  const activePercentage = activeItem ? Math.round((activeItem.value / total) * 100) : null;

  return (
    <div className={`grid items-center gap-4 ${compact ? 'grid-cols-[116px_1fr]' : 'grid-cols-[148px_1fr]'}`}>
      <div className="relative flex items-center justify-center">
        <svg
          viewBox="0 0 120 120"
          className={compact ? 'h-[108px] w-[108px]' : 'h-[138px] w-[138px]'}
          role="img"
          aria-label="Distribuição percentual"
          onPointerLeave={() => setActiveIndex(null)}
        >
          <circle cx="60" cy="60" r={radius} fill="none" stroke="var(--clinic-soft)" strokeWidth="16" />
          {segments.map(({ item, segmentLength, dashOffset }, index) => {
            const percentage = Math.round((item.value / total) * 100);
            const isActive = activeIndex === index;
            const hasActiveSlice = activeIndex !== null;

            return (
              <circle
                key={item.label}
                className="chart-donut-enter cursor-pointer outline-none"
                cx="60"
                cy="60"
                r={radius}
                fill="none"
                stroke={item.color}
                strokeWidth={isActive ? 19 : 16}
                strokeDasharray={`${segmentLength} ${circumference - segmentLength}`}
                strokeDashoffset={dashOffset}
                strokeLinecap="butt"
                transform="rotate(-90 60 60)"
                opacity={hasActiveSlice && !isActive ? 0.48 : 1}
                role="button"
                tabIndex={0}
                aria-label={`${item.label}: ${item.value} (${percentage}%)`}
                data-active={isActive ? 'true' : 'false'}
                style={{
                  '--chart-delay': `${index * 80}ms`,
                  filter: isActive ? `drop-shadow(0 0 3px ${item.color})` : undefined,
                } as CSSProperties}
                onPointerEnter={() => setActiveIndex(index)}
                onFocus={() => setActiveIndex(index)}
                onBlur={() => setActiveIndex(null)}
              />
            );
          })}
          {centerLabel ? (
            <text x="60" y="64" textAnchor="middle" fontSize="12" fontWeight="700" fill="var(--clinic-text)" pointerEvents="none">
              {centerLabel}
            </text>
          ) : null}
        </svg>

        {activeItem && activePercentage !== null ? (
          <div
            role="tooltip"
            className="pointer-events-none absolute left-1/2 top-1/2 min-w-[104px] -translate-x-1/2 -translate-y-1/2 rounded-lg border border-clinic-border bg-clinic-surface-raised px-2.5 py-2 text-center shadow-lg"
          >
            <p className="truncate text-[9px] font-bold text-clinic-text">{activeItem.label}</p>
            <p className="mt-0.5 text-[9px] text-clinic-muted">
              {activeItem.value} · <strong className="text-clinic-text">{activePercentage}%</strong>
            </p>
          </div>
        ) : null}
      </div>

      <div data-testid="donut-legend" className="space-y-2">
        {items.map((item, index) => (
          <div
            key={item.label}
            className={`grid grid-cols-[auto_1fr_auto] items-center gap-2 rounded text-[11px] transition-opacity ${
              activeIndex !== null && activeIndex !== index ? 'opacity-50' : 'opacity-100'
            }`}
            onPointerEnter={() => setActiveIndex(index)}
            onPointerLeave={() => setActiveIndex(null)}
          >
            <span className="h-2 w-2 rounded-full" style={{ backgroundColor: item.color }} />
            <span className="truncate font-semibold text-clinic-text">{item.label}</span>
            <span className="font-bold text-clinic-text">
              {valueMode === 'value' ? item.value : `${Math.round((item.value / total) * 100)}%`}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function buildSegments(items: DonutItem[], total: number, circumference: number) {
  let accumulated = 0;

  return items.map((item) => {
    const segmentLength = (item.value / total) * circumference;
    const dashOffset = -accumulated;
    accumulated += segmentLength;

    return { item, segmentLength, dashOffset };
  });
}
