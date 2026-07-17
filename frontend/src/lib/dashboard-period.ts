export type DashboardFilter = 'SEMANA_ATUAL' | 'MES_ATUAL' | 'MES_ANTERIOR';

export type DashboardPeriodSelection = {
  filter: DashboardFilter;
  backendPeriod: 'SEMANA' | 'MES';
  data: string;
  title: string;
  value: string;
  startDate: string;
  endDate: string;
};

const TIME_ZONE = 'America/Sao_Paulo';

export function getSaoPauloDate(date: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date);
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

export function normalizeDashboardFilter(
  periodo: string | undefined,
  data: string | undefined,
  today: string,
): DashboardFilter {
  if (periodo === 'MES') {
    return data?.slice(0, 7) === today.slice(0, 7) ? 'MES_ATUAL' : 'MES_ANTERIOR';
  }
  return 'SEMANA_ATUAL';
}

export function getDashboardPeriod(
  filter: DashboardFilter,
  today: string,
): DashboardPeriodSelection {
  const monthAnchor = filter === 'MES_ANTERIOR' ? shiftMonths(today, -1) : today;
  const startDate = filter === 'SEMANA_ATUAL' ? startOfWeek(today) : firstOfMonth(monthAnchor);
  const endDate = filter === 'SEMANA_ATUAL'
    ? addDays(startDate, 6)
    : lastOfMonth(monthAnchor);

  return {
    filter,
    backendPeriod: filter === 'SEMANA_ATUAL' ? 'SEMANA' : 'MES',
    data: filter === 'SEMANA_ATUAL' ? today : firstOfMonth(monthAnchor),
    title: filter === 'SEMANA_ATUAL' ? 'SEMANA ATUAL' : filter === 'MES_ATUAL' ? 'MÊS ATUAL' : 'MÊS ANTERIOR',
    value: filter === 'SEMANA_ATUAL'
      ? formatDateRange(startDate, endDate)
      : formatMonth(monthAnchor),
    startDate,
    endDate,
  };
}

export function addDays(value: string, days: number): string {
  const date = parseCalendarDate(value);
  date.setUTCDate(date.getUTCDate() + days);
  return formatCalendarDate(date);
}

function startOfWeek(value: string): string {
  const date = parseCalendarDate(value);
  const day = date.getUTCDay();
  date.setUTCDate(date.getUTCDate() - (day === 0 ? 6 : day - 1));
  return formatCalendarDate(date);
}

function firstOfMonth(value: string): string {
  const date = parseCalendarDate(value);
  date.setUTCDate(1);
  return formatCalendarDate(date);
}

function lastOfMonth(value: string): string {
  const date = parseCalendarDate(value);
  date.setUTCMonth(date.getUTCMonth() + 1, 0);
  return formatCalendarDate(date);
}

function shiftMonths(value: string, months: number): string {
  const date = parseCalendarDate(value);
  date.setUTCDate(1);
  date.setUTCMonth(date.getUTCMonth() + months);
  return formatCalendarDate(date);
}

function parseCalendarDate(value: string): Date {
  return new Date(`${value}T12:00:00Z`);
}

function formatCalendarDate(value: Date): string {
  return [value.getUTCFullYear(), value.getUTCMonth() + 1, value.getUTCDate()]
    .map((part, index) => index === 0 ? String(part) : String(part).padStart(2, '0'))
    .join('-');
}

function formatDateRange(startDate: string, endDate: string): string {
  return `${formatBrazilianDate(startDate)} – ${formatBrazilianDate(endDate)}`;
}

function formatBrazilianDate(value: string): string {
  const [year, month, day] = value.split('-');
  return `${day}/${month}/${year}`;
}

function formatMonth(value: string): string {
  const [, year, month] = value.match(/^(\d{4})-(\d{2})-/) ?? [];
  return `${month}/${year}`;
}
