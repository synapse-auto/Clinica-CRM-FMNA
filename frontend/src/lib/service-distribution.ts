export type ServiceSource = {
  servicoNome?: string | null;
  tipo?: string | null;
};

export type ServiceDistribution = {
  label: string;
  total: number;
  percentual: number;
};

export const SERVICE_COLORS = [
  'var(--clinic-primary)',
  'var(--clinic-blue)',
  'var(--clinic-indigo)',
  'var(--clinic-cyan)',
  'var(--clinic-orange)',
  'var(--clinic-pink)',
];

export const SERVICE_FALLBACK = 'Não informado';

export function normalizeServiceName(source: ServiceSource): string {
  return source.servicoNome?.trim() || source.tipo?.trim() || SERVICE_FALLBACK;
}

export function aggregateServices(appointments: ServiceSource[]): ServiceDistribution[] {
  const counts = new Map<string, number>();
  appointments.forEach((appointment) => {
    const label = normalizeServiceName(appointment);
    counts.set(label, (counts.get(label) ?? 0) + 1);
  });

  const total = appointments.length;
  return Array.from(counts.entries())
    .map(([label, count]) => ({
      label,
      total: count,
      percentual: total === 0 ? 0 : Math.round((count * 1000) / total) / 10,
    }))
    .sort((left, right) => right.total - left.total || left.label.localeCompare(right.label, 'pt-BR'));
}

export function serviceColor(index: number): string {
  return SERVICE_COLORS[index % SERVICE_COLORS.length];
}
