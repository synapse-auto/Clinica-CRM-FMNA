export function normalizeSearchText(value: unknown) {
  return String(value ?? '')
    .trim()
    .toLocaleLowerCase('pt-BR')
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
    .replace(/\s+/g, ' ');
}

export function normalizeDigits(value: unknown) {
  return String(value ?? '').replace(/\D/g, '');
}

export function matchesSearchTokens(values: unknown[], search: unknown) {
  const term = normalizeSearchText(search);
  if (!term) return true;
  const haystack = normalizeSearchText(values.join(' '));
  const tokens = term.split(' ').filter(Boolean);
  const digits = normalizeDigits(search);
  return tokens.every((token) => haystack.includes(token))
    || (Boolean(digits) && values.some((value) => normalizeDigits(value).includes(digits)));
}

export function isSearchableTerm(value: unknown) {
  const raw = String(value ?? '').trim();
  const digits = normalizeDigits(raw);
  const onlyNumeric = Boolean(digits) && raw.replace(/[\d\s+()./-]/g, '') === '';
  return onlyNumeric || normalizeSearchText(raw).length >= 2;
}
