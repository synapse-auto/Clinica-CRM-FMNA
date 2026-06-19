'use client';

import { useId, useState } from 'react';

type LineDatum = {
  label: string;
  value: number;
};

const chartWidth = 760;
const chartHeight = 205;
const plotTop = 18;
const plotBottom = chartHeight - 34;
const plotLeft = 48;
const plotRight = chartWidth - 20;
const tooltipWidth = 112;

export function LineAreaChart({ data }: { data: LineDatum[] }) {
  const [activeIndex, setActiveIndex] = useState<number | null>(null);
  const gradientId = `message-area-${useId().replaceAll(':', '')}`;
  const safeData = data.length > 1 ? data : [{ label: '00h', value: 0 }, { label: '24h', value: 0 }];
  const maxValue = Math.max(...safeData.map((item) => item.value), 1);
  const points = safeData.map((item, index) => ({
    x: plotLeft + (index * (plotRight - plotLeft)) / (safeData.length - 1),
    y: plotTop + (1 - item.value / maxValue) * (plotBottom - plotTop),
  }));
  const linePath = createSmoothPath(points);
  const areaPath = `${linePath} L ${points.at(-1)?.x ?? 0} ${plotBottom} L ${points[0].x} ${plotBottom} Z`;
  const activeItem = activeIndex === null ? null : safeData[activeIndex];
  const activePoint = activeIndex === null ? null : points[activeIndex];

  return (
    <div className="h-[214px] w-full px-3 pb-2 pt-1">
      <svg
        viewBox={`0 0 ${chartWidth} ${chartHeight}`}
        className="h-full w-full"
        role="img"
        aria-label="Pico de mensagens por hora"
        onPointerLeave={() => setActiveIndex(null)}
      >
        <defs>
          <linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="var(--clinic-blue)" stopOpacity="0.22" />
            <stop offset="100%" stopColor="var(--clinic-blue)" stopOpacity="0" />
          </linearGradient>
        </defs>

        {[0, 1, 2, 3].map((line) => {
          const y = plotTop + line * ((plotBottom - plotTop) / 3);
          return (
            <g key={line}>
              <line x1={plotLeft} x2={chartWidth - 18} y1={y} y2={y} stroke="var(--clinic-grid)" strokeDasharray="3 4" />
              <text x="38" y={y + 4} textAnchor="end" fontSize="9" fill="var(--clinic-muted)">
                {Math.round(maxValue - (maxValue * line) / 3)}
              </text>
            </g>
          );
        })}

        {safeData.map((item, index) => (
          <g key={`${item.label}-${index}`}>
            {shouldShowAxisLabel(index, safeData.length) ? (
              <>
                <line
                  x1={points[index].x}
                  x2={points[index].x}
                  y1={plotTop}
                  y2={plotBottom}
                  stroke="var(--clinic-grid)"
                  strokeDasharray="3 4"
                />
                <text x={points[index].x} y={chartHeight - 12} textAnchor="middle" fontSize="9" fill="var(--clinic-muted)">
                  {item.label}
                </text>
              </>
            ) : null}
          </g>
        ))}

        <path
          data-testid="line-chart-area"
          className="chart-area-enter"
          d={areaPath}
          fill={`url(#${gradientId})`}
        />
        <path
          data-testid="line-chart-line"
          className="chart-line-enter"
          d={linePath}
          fill="none"
          stroke="var(--clinic-blue)"
          strokeWidth="2.4"
          strokeLinecap="round"
          pathLength="1"
        />

        {activePoint && activeItem ? (
          <g>
            <line
              data-testid="line-chart-active-guide"
              x1={activePoint.x}
              x2={activePoint.x}
              y1={plotTop}
              y2={plotBottom}
              stroke="var(--clinic-muted)"
              strokeWidth="1"
              strokeDasharray="3 3"
            />
            <circle
              data-testid="line-chart-active-point"
              cx={activePoint.x}
              cy={activePoint.y}
              r="5"
              fill="var(--clinic-surface)"
              stroke="var(--clinic-blue)"
              strokeWidth="3"
            />
            <ChartTooltip
              x={Math.min(Math.max(activePoint.x + 10, plotLeft), chartWidth - tooltipWidth - 8)}
              y={Math.max(activePoint.y - 52, 8)}
              label={activeItem.label}
              value={activeItem.value}
            />
          </g>
        ) : null}

        {safeData.map((item, index) => {
          const previousX = points[index - 1]?.x ?? plotLeft;
          const nextX = points[index + 1]?.x ?? plotRight;
          const hitStart = index === 0 ? plotLeft : (previousX + points[index].x) / 2;
          const hitEnd = index === safeData.length - 1 ? plotRight : (points[index].x + nextX) / 2;

          return (
            <rect
              key={`hit-${item.label}-${index}`}
              x={hitStart}
              y={plotTop}
              width={Math.max(hitEnd - hitStart, 1)}
              height={plotBottom - plotTop}
              fill="transparent"
              role="button"
              tabIndex={0}
              aria-label={`Mensagens em ${item.label}: ${item.value}`}
              onPointerEnter={() => setActiveIndex(index)}
              onFocus={() => setActiveIndex(index)}
              onBlur={() => setActiveIndex(null)}
            />
          );
        })}
      </svg>
    </div>
  );
}

function ChartTooltip({ x, y, label, value }: { x: number; y: number; label: string; value: number }) {
  return (
    <g role="tooltip" transform={`translate(${x} ${y})`} pointerEvents="none">
      <rect width={tooltipWidth} height="44" rx="7" fill="var(--clinic-surface-raised)" stroke="var(--clinic-border)" />
      <text x="9" y="16" fontSize="9" fontWeight="700" fill="var(--clinic-text)">
        {label}
      </text>
      <text x="9" y="32" fontSize="9" fill="var(--clinic-muted)">
        Mensagens:
      </text>
      <text x="103" y="32" textAnchor="end" fontSize="9" fontWeight="700" fill="var(--clinic-blue)">
        {value}
      </text>
    </g>
  );
}

function shouldShowAxisLabel(index: number, length: number) {
  if (length <= 8) {
    return true;
  }
  return index % 4 === 0 || index === length - 1;
}

function createSmoothPath(points: Array<{ x: number; y: number }>) {
  if (points.length < 2) {
    return '';
  }

  return points.slice(1).reduce((path, point, index) => {
    const previous = points[index];
    const controlX = (previous.x + point.x) / 2;
    return `${path} C ${controlX} ${previous.y}, ${controlX} ${point.y}, ${point.x} ${point.y}`;
  }, `M ${points[0].x} ${points[0].y}`);
}
