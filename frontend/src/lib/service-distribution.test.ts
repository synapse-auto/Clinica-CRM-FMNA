import { describe, expect, it } from 'vitest';
import { aggregateServices, normalizeServiceName } from './service-distribution';

describe('service distribution', () => {
  it('should_use_real_service_name_and_fallback_only_when_missing', () => {
    expect(normalizeServiceName({ servicoNome: 'Ultrassonografia', tipo: 'EXAME' })).toBe('Ultrassonografia');
    expect(normalizeServiceName({ servicoNome: '', tipo: 'EXAME' })).toBe('EXAME');
    expect(normalizeServiceName({ servicoNome: null, tipo: null })).toBe('Não informado');
  });

  it('should_aggregate_services_in_descending_order_with_consistent_percentages', () => {
    expect(aggregateServices([
      { servicoNome: 'Ultrassonografia' },
      { servicoNome: 'Consulta ginecológica' },
      { servicoNome: 'Ultrassonografia' },
      { servicoNome: null, tipo: null },
    ])).toEqual([
      { label: 'Ultrassonografia', total: 2, percentual: 50 },
      { label: 'Consulta ginecológica', total: 1, percentual: 25 },
      { label: 'Não informado', total: 1, percentual: 25 },
    ]);
  });
});
