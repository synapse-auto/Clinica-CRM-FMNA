function parseValidDate(value: string | null | undefined): Date | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function isSameLocalDate(first: Date, second: Date) {
  return first.getFullYear() === second.getFullYear()
    && first.getMonth() === second.getMonth()
    && first.getDate() === second.getDate();
}

function formatTime(date: Date) {
  return new Intl.DateTimeFormat('pt-BR', {
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(date);
}

export function formatWhatsappWindowExpiration(
  expiresAt: string | null | undefined,
  now: Date,
): string | null {
  const expiration = parseValidDate(expiresAt);
  if (!expiration || Number.isNaN(now.getTime()) || expiration.getTime() < now.getTime()) return null;

  const time = formatTime(expiration);
  if (isSameLocalDate(expiration, now)) return `Fecha hoje às ${time}`;

  const tomorrow = new Date(now);
  tomorrow.setDate(tomorrow.getDate() + 1);
  if (isSameLocalDate(expiration, tomorrow)) return `Fecha amanhã às ${time}`;

  if (expiration.getFullYear() === now.getFullYear()) {
    const weekday = new Intl.DateTimeFormat('pt-BR', { weekday: 'long' }).format(expiration);
    const date = new Intl.DateTimeFormat('pt-BR', {
      day: '2-digit',
      month: '2-digit',
    }).format(expiration);
    return `Fecha ${weekday}, ${date} às ${time}`;
  }

  const date = new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(expiration);
  return `Fecha ${date} às ${time}`;
}

export function formatWhatsappWindowExpirationTitle(expiresAt: string | null | undefined): string | null {
  const expiration = parseValidDate(expiresAt);
  if (!expiration) return null;
  const date = new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(expiration);
  return `${date} às ${formatTime(expiration)}`;
}
