import { describe, expect, it } from 'vitest';
import { formatChatDateLabel, formatChatMessageTime, getChatDateKey } from './chat-date';

describe('chat-date', () => {
  const now = new Date(2026, 6, 30, 0, 10);

  it('should_format_today_and_yesterday_by_local_calendar_day', () => {
    expect(formatChatDateLabel(new Date(2026, 6, 30, 0, 5), now)).toBe('Hoje');
    expect(formatChatDateLabel(new Date(2026, 6, 29, 23, 55), now)).toBe('Ontem');
  });

  it('should_format_dates_from_the_same_and_another_year', () => {
    expect(formatChatDateLabel(new Date(2026, 6, 28, 12), now)).toBe('terça-feira, 28 de julho');
    expect(formatChatDateLabel(new Date(2025, 11, 31, 12), now)).toBe('31 de dezembro de 2025');
  });

  it('should_return_safe_fallbacks_for_invalid_dates', () => {
    expect(getChatDateKey('invalid')).toBeNull();
    expect(formatChatMessageTime('invalid')).toBe('Horário indisponível');
  });
});
