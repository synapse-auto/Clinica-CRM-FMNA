import { describe, expect, it } from 'vitest';
import {
  isSearchableTerm,
  matchesSearchTokens,
  normalizeDigits,
  normalizeSearchText,
} from './search';

describe('normalizeSearchText', () => {
  it('normalizes accents, case and repeated spaces', () => {
    expect(normalizeSearchText('  Jo\u00e3o   L\u00f3pes  ')).toBe('joao lopes');
  });

  it('normalizes formatted phone numbers to digits', () => {
    expect(normalizeDigits('(83) 99999-9999')).toBe('83999999999');
  });

  it('matches all words regardless of order and punctuation', () => {
    expect(matchesSearchTokens(['Jo\u00e3o da Silva'], 'silva joao')).toBe(true);
    expect(matchesSearchTokens(['Jo\u00e3o da Silva'], 'joao maria')).toBe(false);
    expect(normalizeSearchText('Confirma\u00e7\u00e3o_M\u00c9DICA---retorno')).toBe('confirmacao medica retorno');
  });

  it('matches formatted phone digits and avoids expensive one-letter searches', () => {
    expect(matchesSearchTokens(['+55 (83) 99999-0000'], '83999990000')).toBe(true);
    expect(isSearchableTerm('a')).toBe(false);
    expect(isSearchableTerm('42')).toBe(true);
    expect(isSearchableTerm('jo')).toBe(true);
  });
});
