import { describe, expect, it } from 'vitest';
import {
  getDashboardPeriod,
  getSaoPauloDate,
  normalizeDashboardFilter,
} from './dashboard-period';

describe('dashboard period helpers', () => {
  it('should_use_sao_paulo_calendar_date', () => {
    expect(getSaoPauloDate(new Date('2026-07-17T02:30:00Z'))).toBe('2026-07-16');
  });

  it('should_build_current_week_interval_from_the_same_anchor_sent_to_backend', () => {
    const period = getDashboardPeriod('SEMANA_ATUAL', '2026-07-17');

    expect(period.backendPeriod).toBe('SEMANA');
    expect(period.data).toBe('2026-07-17');
    expect(period.startDate).toBe('2026-07-13');
    expect(period.endDate).toBe('2026-07-19');
    expect(period.value).toBe('13/07/2026 – 19/07/2026');
  });

  it('should_build_current_and_previous_month_labels_without_timezone_shift', () => {
    expect(getDashboardPeriod('MES_ATUAL', '2026-07-17')).toMatchObject({
      backendPeriod: 'MES',
      data: '2026-07-01',
      value: '07/2026',
    });
    expect(getDashboardPeriod('MES_ANTERIOR', '2026-07-17')).toMatchObject({
      backendPeriod: 'MES',
      data: '2026-06-01',
      value: '06/2026',
    });
  });

  it('should_keep_one_filter_selected_for_legacy_dashboard_urls', () => {
    expect(normalizeDashboardFilter('DIA', '2026-07-17', '2026-07-17')).toBe('SEMANA_ATUAL');
    expect(normalizeDashboardFilter('MES', '2026-07-17', '2026-07-17')).toBe('MES_ATUAL');
    expect(normalizeDashboardFilter('MES', '2026-06-17', '2026-07-17')).toBe('MES_ANTERIOR');
  });
});
