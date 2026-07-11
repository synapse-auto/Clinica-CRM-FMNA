import { describe, expect, it } from 'vitest';
import { isValidPassword, PASSWORD_RULE_MESSAGE } from './password-policy';

describe('password-policy', () => {
  it.each(['Senha@123', 'Ultra#2026', 'Acesso!123', 'Minha_Senha9', 'Senha123', ' Senha1 '])(
    'should_accept_valid_password_%s',
    (password) => {
      expect(isValidPassword(password)).toBe(true);
    },
  );

  it.each(['123456', 'abcdef', 'ab12'])(
    'should_reject_invalid_password_%s',
    (password) => {
      expect(isValidPassword(password)).toBe(false);
    },
  );

  it('should_enforce_bcrypt_72_byte_limit', () => {
    expect(isValidPassword(`a1${'x'.repeat(70)}`)).toBe(true);
    expect(isValidPassword(`a1${'x'.repeat(71)}`)).toBe(false);
    expect(isValidPassword(`a1${'á'.repeat(36)}`)).toBe(false);
  });

  it('should_explain_that_special_characters_are_allowed', () => {
    expect(PASSWORD_RULE_MESSAGE).toContain('Caracteres especiais são permitidos.');
  });
});
