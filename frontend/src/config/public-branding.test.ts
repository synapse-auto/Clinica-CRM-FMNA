import { describe, expect, it } from 'vitest';
import { buildDocumentTitle } from './public-branding';

describe('public branding document title', () => {
  it('uses the configured clinic name', () => {
    expect(buildDocumentTitle('UltraMedical')).toBe('CRM UltraMedical');
    expect(buildDocumentTitle('FMNA')).toBe('CRM FMNA');
  });

  it('uses a professional fallback without duplicating CRM', () => {
    expect(buildDocumentTitle('CRM Clínico')).toBe('CRM de Atendimento Clínico');
    expect(buildDocumentTitle('')).toBe('CRM de Atendimento Clínico');
    expect(buildDocumentTitle('CRM UltraMedical')).toBe('CRM UltraMedical');
  });
});
