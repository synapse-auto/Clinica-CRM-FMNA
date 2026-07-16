import { describe, expect, it } from 'vitest';
import { normalizeDigits, normalizeSearchText } from './search';

describe('normalizeSearchText', () => {
  it('normalizes accents, case and repeated spaces', () => {
    expect(normalizeSearchText('  Jo\u00e3o   L\u00f3pes  ')).toBe('joao lopes');
  });

  it('normalizes formatted phone numbers to digits', () => {
    expect(normalizeDigits('(83) 99999-9999')).toBe('83999999999');
  });
});
