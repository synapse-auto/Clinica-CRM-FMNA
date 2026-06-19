'use client';

import type { CSSProperties } from 'react';

type AnimatedProgressProps = {
  value: number;
  label: string;
  className: string;
};

export function AnimatedProgress({ value, label, className }: AnimatedProgressProps) {
  const normalizedValue = Math.max(0, Math.min(100, value));

  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={normalizedValue}
      className={`chart-progress-enter h-1 w-full rounded-full ${className}`}
      style={{ '--progress-value': normalizedValue / 100 } as CSSProperties}
    />
  );
}
