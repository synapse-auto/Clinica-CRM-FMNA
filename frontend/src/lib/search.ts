export function normalizeSearchText(value: unknown) {
  return String(value ?? '')
    .trim()
    .toLocaleLowerCase('pt-BR')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/\s+/g, ' ');
}

export function normalizeDigits(value: unknown) {
  return String(value ?? '').replace(/\D/g, '');
}
