export const PASSWORD_MIN_LENGTH = 6;
export const PASSWORD_MAX_BYTES = 72;
export const PASSWORD_RULE_MESSAGE = 'A senha deve ter pelo menos 6 caracteres, incluindo uma letra e um número, e no máximo 72 bytes. Caracteres especiais são permitidos.';

export function isValidPassword(password: string) {
  return Array.from(password).length >= PASSWORD_MIN_LENGTH
    && new TextEncoder().encode(password).length <= PASSWORD_MAX_BYTES
    && /\p{L}/u.test(password)
    && /\p{N}/u.test(password);
}
