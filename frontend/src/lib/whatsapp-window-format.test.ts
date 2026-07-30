import { describe, expect, it } from 'vitest';
import {
  formatWhatsappWindowExpiration,
  formatWhatsappWindowExpirationTitle,
} from './whatsapp-window-format';

describe('formatWhatsappWindowExpiration', () => {
  it('should_format_an_expiration_on_the_same_local_day', () => {
    expect(formatWhatsappWindowExpiration(
      '2026-07-30T19:00:00-03:00',
      new Date('2026-07-30T10:00:00-03:00'),
    )).toBe('Fecha hoje às 19:00');
  });

  it('should_format_an_expiration_on_the_next_local_day', () => {
    expect(formatWhatsappWindowExpiration(
      '2026-07-31T22:00:00-03:00',
      new Date('2026-07-30T10:00:00-03:00'),
    )).toBe('Fecha amanhã às 22:00');
  });

  it('should_use_civil_dates_when_the_window_crosses_midnight', () => {
    expect(formatWhatsappWindowExpiration(
      '2026-07-31T00:15:00-03:00',
      new Date('2026-07-30T23:30:00-03:00'),
    )).toBe('Fecha amanhã às 00:15');
  });

  it('should_keep_an_expiration_later_on_the_same_day_as_today', () => {
    expect(formatWhatsappWindowExpiration(
      '2026-07-30T23:50:00-03:00',
      new Date('2026-07-30T00:10:00-03:00'),
    )).toBe('Fecha hoje às 23:50');
  });

  it('should_include_weekday_and_day_month_for_another_date_in_the_same_year', () => {
    expect(formatWhatsappWindowExpiration(
      '2026-08-07T22:00:00-03:00',
      new Date('2026-07-30T10:00:00-03:00'),
    )).toBe('Fecha sexta-feira, 07/08 às 22:00');
  });

  it('should_include_the_year_for_an_expiration_in_another_year', () => {
    expect(formatWhatsappWindowExpiration(
      '2027-01-02T08:15:00-03:00',
      new Date('2026-12-30T10:00:00-03:00'),
    )).toBe('Fecha 02/01/2027 às 08:15');
  });

  it('should_not_format_invalid_or_expired_dates', () => {
    const now = new Date('2026-07-30T10:00:00-03:00');
    expect(formatWhatsappWindowExpiration(null, now)).toBeNull();
    expect(formatWhatsappWindowExpiration('', now)).toBeNull();
    expect(formatWhatsappWindowExpiration('not-a-date', now)).toBeNull();
    expect(formatWhatsappWindowExpiration('2026-07-30T09:59:00-03:00', now)).toBeNull();
  });

  it('should_format_a_complete_title_date_when_the_expiration_is_valid', () => {
    expect(formatWhatsappWindowExpirationTitle('2026-07-31T22:54:00-03:00'))
      .toBe('31/07/2026 às 22:54');
  });
});
