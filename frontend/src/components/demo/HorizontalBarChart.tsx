'use client';

import { type CSSProperties } from 'react';

export type HorizontalBarSeries = {
  label: string;
  color: string;
  values: number[];
};

type HorizontalBarChartProps = {
  /** Categorias exibidas à esquerda (ex.: nomes de médicos). Uma entrada por linha. */
  labels: string[];
  /** Séries proporcionais por categoria (ex.: tipos de serviço). */
  series: HorizontalBarSeries[];
  /** Altura máxima da área rolável; acima disso surge scroll vertical interno. */
  maxHeight?: number;
  emptyMessage?: string;
  /** Descrição acessível do gráfico como um todo. */
  ariaLabel?: string;
};

// Piso VISUAL em pixels (não em %) para valores positivos: mantém uma barra fina visível
// sem inflar a proporção real (a escala continua sendo a largura percentual verdadeira).
const MIN_VISIBLE_BAR_PX = 3;

/**
 * Gráfico de barras HORIZONTAL para categorias com nomes longos (ex.: "Agenda por Médico").
 * Nome à esquerda (truncado + title), barra proporcional e valor legível; a altura acompanha a
 * quantidade de itens e usa scroll vertical interno em datasets grandes — sem sobreposição de texto.
 */
export function HorizontalBarChart({
  labels,
  series,
  maxHeight = 230,
  emptyMessage = 'Sem dados no período.',
  ariaLabel = 'Distribuição por categoria',
}: HorizontalBarChartProps) {
  if (labels.length === 0 || series.length === 0) {
    return (
      <div
        className="flex min-h-[120px] items-center justify-center px-5 pb-4 text-center text-[11px] text-clinic-muted"
        data-testid="hbar-empty"
      >
        {emptyMessage}
      </div>
    );
  }

  const maxValue = Math.max(1, ...series.flatMap((item) => item.values));
  const multiSeries = series.length > 1;

  return (
    <div className="flex w-full flex-col gap-2 px-4 pb-4">
      {multiSeries ? (
        <div
          data-testid="hbar-legend"
          className="flex flex-wrap justify-end gap-x-3 gap-y-1 text-[9px] text-clinic-muted"
        >
          {series.map((item) => (
            <span key={item.label} className="inline-flex items-center gap-1.5">
              <span className="h-2 w-2 shrink-0 rounded-sm" style={{ backgroundColor: item.color }} />
              <span className="max-w-[160px] truncate" title={item.label}>{item.label}</span>
            </span>
          ))}
        </div>
      ) : null}

      <ul
        role="list"
        aria-label={ariaLabel}
        data-testid="hbar-rows"
        className="flex flex-col gap-3 overflow-y-auto pr-1 custom-scrollbar"
        style={{ maxHeight }}
      >
        {labels.map((label, groupIndex) => {
          const total = series.reduce((sum, item) => sum + (item.values[groupIndex] ?? 0), 0);
          return (
            <li key={`${label}-${groupIndex}`} className="flex flex-col gap-1" data-testid="hbar-row">
              <div className="flex items-baseline justify-between gap-2 text-[11px]">
                <span className="min-w-0 flex-1 truncate font-semibold text-clinic-text" title={label}>
                  {label}
                </span>
                {multiSeries ? (
                  <span className="shrink-0 font-bold tabular-nums text-clinic-muted">{total}</span>
                ) : null}
              </div>
              <div className="flex flex-col gap-1">
                {series.map((item) => {
                  const value = item.values[groupIndex] ?? 0;
                  const ratio = value / maxValue;
                  // Largura = proporção REAL (não infla a escala). O mínimo visível é aplicado
                  // como min-width em pixels (só para valores positivos), não como piso percentual.
                  const widthPct = value > 0 ? ratio * 100 : 0;
                  return (
                    <div
                      key={item.label}
                      className="flex items-center gap-2"
                      title={`${label} · ${item.label}: ${value}`}
                    >
                      <div className="h-2.5 min-w-0 flex-1 overflow-hidden rounded-full bg-clinic-soft" aria-hidden="true">
                        <div
                          data-testid="hbar-fill"
                          className="h-full rounded-full transition-[width] duration-300"
                          style={{
                            width: `${widthPct}%`,
                            minWidth: value > 0 ? `${MIN_VISIBLE_BAR_PX}px` : undefined,
                            backgroundColor: item.color,
                          } as CSSProperties}
                        />
                      </div>
                      <span
                        className="w-7 shrink-0 text-right text-[10px] font-semibold tabular-nums text-clinic-muted"
                        aria-label={multiSeries ? `${item.label}: ${value}` : undefined}
                      >
                        {value}
                      </span>
                    </div>
                  );
                })}
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
