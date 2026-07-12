'use client';

import { useState } from 'react';

type Props = {
  name: string;
  url?: string | null;
  variant?: 'list' | 'details';
};

export function ContactAvatar({ name, url, variant = 'list' }: Props) {
  const safeUrl = validHttpsUrl(url);
  const imageKey = safeUrl ? `${name}\u0000${safeUrl}` : null;
  const [failedImageKey, setFailedImageKey] = useState<string | null>(null);
  const showImage = imageKey !== null && imageKey !== failedImageKey;
  const details = variant === 'details';

  return (
    <div
      className={details
        ? 'relative mx-auto mb-3 flex h-[72px] w-[72px] shrink-0 items-center justify-center overflow-hidden rounded-full bg-clinic-primary/15 text-xl font-extrabold text-clinic-primary ring-4 ring-clinic-soft'
        : 'relative flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-full bg-clinic-primary/15 text-sm font-extrabold text-clinic-primary'}
    >
      {showImage && safeUrl ? (
        <img
          src={safeUrl}
          alt={name}
          className="absolute inset-0 h-full w-full rounded-full object-cover"
          onError={() => setFailedImageKey(imageKey)}
        />
      ) : (
        <span>{initials(name)}</span>
      )}
    </div>
  );
}

function initials(name: string) {
  return name
    .normalize('NFKD')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => Array.from(part)[0]?.toUpperCase())
    .join('');
}

function validHttpsUrl(value?: string | null) {
  if (typeof value !== 'string' || !value.trim()) return null;
  try {
    const url = new URL(value.trim());
    return url.protocol === 'https:' && !url.username && !url.password ? url.toString() : null;
  } catch {
    return null;
  }
}
