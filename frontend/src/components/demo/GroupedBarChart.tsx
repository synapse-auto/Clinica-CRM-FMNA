'use client';

import { useState, type CSSProperties } from 'react';

type BarSeries = {
  label: string;
  color: string;
  values: number[];
};

type ActiveBar = {
  groupIndex: number;
  seriesIndex: number;
};

const chartWidth = 760;
const chartHeight = 205;
const tooltipWidth = 118;

export function GroupedBarChart({
  labels,
  series,
  height = 206,
}: {
  labels: string[];
  series: BarSeries[];
  height?: number;
}) {
  const [activeBar, setActiveBar] = useState<ActiveBar | null>(null);
  const maxValue = Math.max(...series.flatMap((item) => item.values), 1);
  const plotLeft = 24;
  const plotRight = chartWidth - 16;
  const plotBottom = chartHeight - 34;
  const plotTop = 14;
  const groupWidth = (plotRight - plotLeft) / Math.max(labels.length, 1);
  const barGap = 4;
  const barWidth = Math.max(4, Math.min(36, (groupWidth - 18 - barGap * (series.length - 1)) / Math.max(series.length, 1)));
  const activeGeometry = activeBar ? getBarGeometry(activeBar.groupIndex, activeBar.seriesIndex) : null;
  const activeSeries = activeBar ? series[activeBar.seriesIndex] : null;
  const activeValue = activeBar && activeSeries ? activeSeries.values[activeBar.groupIndex] ?? 0 : null;

  function getBarGeometry(groupIndex: number, seriesIndex: number) {
    const totalBarsWidth = series.length * barWidth + (series.length - 1) * barGap;
    const groupCenter = plotLeft + groupIndex * groupWidth + groupWidth / 2;
    const startX = groupCenter - totalBarsWidth / 2;
    const value = series[seriesIndex]?.values[groupIndex] ?? 0;
    const barHeight = Math.max(2, (value / maxValue) * (plotBottom - plotTop - 8));

    return {
      x: startX + seriesIndex * (barWidth + barGap),
      y: plotBottom - barHeight,
      height: barHeight,
    };
  }

  return (
    <div className="flex w-full flex-col px-3 pb-2" style={{ height }}>
      <div data-testid="bar-chart-legend" className="mb-1 flex flex-wrap justify-end gap-x-3 gap-y-1 px-2 text-[9px] text-clinic-muted">
        {series.map((item) => (
          <span key={item.label} className="inline-flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-sm" style={{ backgroundColor: item.color }} />
            {item.label}
          </span>
        ))}
      </div>

      <svg
        viewBox={`0 0 ${chartWidth} ${chartHeight}`}
        className="min-h-0 w-full flex-1"
        role="img"
        aria-label="Agendamentos da semana"
        onPointerLeave={() => setActiveBar(null)}
      >
        {[0, 1, 2].map((line) => {
          const y = plotTop + line * ((plotBottom - plotTop) / 2);
          return <line key={line} x1={plotLeft} x2={plotRight} y1={y} y2={y} stroke="var(--clinic-grid)" />;
        })}

        {labels.map((label, groupIndex) => {
          const groupCenter = plotLeft + groupIndex * groupWidth + groupWidth / 2;

          return (
            <g key={`${label}-${groupIndex}`}>
              {series.map((item, seriesIndex) => {
                const value = item.values[groupIndex] ?? 0;
                const geometry = getBarGeometry(groupIndex, seriesIndex);
                const isActive = activeBar?.groupIndex === groupIndex && activeBar.seriesIndex === seriesIndex;
                const hasActiveBar = activeBar !== null;

                return (
                  <rect
                    key={item.label}
                    className="chart-bar-enter cursor-pointer outline-none"
                    x={geometry.x}
                    y={geometry.y}
                    width={barWidth}
                    height={geometry.height}
                    rx="3"
                    fill={item.color}
                    stroke={isActive ? 'var(--clinic-text)' : 'transparent'}
                    strokeWidth={isActive ? 1.5 : 0}
                    opacity={hasActiveBar && !isActive ? 0.5 : 1}
                    role="button"
                    tabIndex={0}
                    aria-label={`${label}, ${item.label}: ${value}`}
                    data-active={isActive ? 'true' : 'false'}
                    style={{ '--chart-delay': `${groupIndex * 55 + seriesIndex * 35}ms` } as CSSProperties}
                    onPointerEnter={() => setActiveBar({ groupIndex, seriesIndex })}
                    onFocus={() => setActiveBar({ groupIndex, seriesIndex })}
                    onBlur={() => setActiveBar(null)}
                  />
                );
              })}
              <text x={groupCenter} y={chartHeight - 12} textAnchor="middle" fontSize="9.5" fill="var(--clinic-muted)">
                {label}
              </text>
            </g>
          );
        })}

        {activeBar && activeGeometry && activeSeries && activeValue !== null ? (
          <BarTooltip
            x={Math.min(Math.max(activeGeometry.x + barWidth + 8, plotLeft), chartWidth - tooltipWidth - 8)}
            y={Math.max(activeGeometry.y - 48, 8)}
            category={labels[activeBar.groupIndex]}
            series={activeSeries.label}
            value={activeValue}
            color={activeSeries.color}
          />
        ) : null}
      </svg>
    </div>
  );
}

function BarTooltip({
  x,
  y,
  category,
  series,
  value,
  color,
}: {
  x: number;
  y: number;
  category: string;
  series: string;
  value: number;
  color: string;
}) {
  return (
    <g role="tooltip" transform={`translate(${x} ${y})`} pointerEvents="none">
      <rect width={tooltipWidth} height="46" rx="7" fill="var(--clinic-surface-raised)" stroke="var(--clinic-border)" />
      <text x="9" y="16" fontSize="9" fontWeight="700" fill="var(--clinic-text)">
        {category}
      </text>
      <circle cx="12" cy="31" r="3" fill={color} />
      <text x="20" y="34" fontSize="9" fill="var(--clinic-muted)">
        {series}
      </text>
      <text x="109" y="34" textAnchor="end" fontSize="9" fontWeight="700" fill="var(--clinic-text)">
        {value}
      </text>
    </g>
  );
}
