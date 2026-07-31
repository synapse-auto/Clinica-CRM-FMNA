function toValidDate(value: string | Date): Date | null {
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function getChatDateKey(value: string | Date): string | null {
  const date = toValidDate(value);
  if (!date) return null;
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function formatChatDateLabel(value: string | Date, now: Date): string | null {
  const date = toValidDate(value);
  if (!date) return null;
  const dateKey = getChatDateKey(date);
  if (dateKey === getChatDateKey(now)) return 'Hoje';

  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (dateKey === getChatDateKey(yesterday)) return 'Ontem';

  if (date.getFullYear() === now.getFullYear()) {
    return new Intl.DateTimeFormat('pt-BR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    }).format(date);
  }

  return new Intl.DateTimeFormat('pt-BR', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(date);
}

export function formatChatDateAccessibleLabel(value: string | Date): string | null {
  const date = toValidDate(value);
  if (!date) return null;
  return new Intl.DateTimeFormat('pt-BR', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(date);
}

export function formatChatMessageTime(value: string): string {
  const date = toValidDate(value);
  if (!date) return 'Horário indisponível';
  return new Intl.DateTimeFormat('pt-BR', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function formatChatMessageDateTime(value: string | Date): string {
  const date = toValidDate(value);
  if (!date) return 'Data e horário indisponíveis';

  const datePart = new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
  }).format(date);
  const timePart = new Intl.DateTimeFormat('pt-BR', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);

  return `${datePart} ${timePart}`;
}
