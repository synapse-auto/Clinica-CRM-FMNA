import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { buildDocumentTitle, publicFavicon } from './public-branding';

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

describe('publicFavicon', () => {
  it('accepts local public paths', () => {
    expect(publicFavicon('/ultramedical-favicon.png')).toBe('/ultramedical-favicon.png');
    expect(publicFavicon('/favicon.ico')).toBe('/favicon.ico');
    expect(publicFavicon('/branding/fmna.png')).toBe('/branding/fmna.png');
  });

  it('falls back for missing or unsafe paths', () => {
    expect(publicFavicon()).toBe('/favicon.ico');
    expect(publicFavicon('')).toBe('/favicon.ico');
    expect(publicFavicon('//host/icon.png')).toBe('/favicon.ico');
    expect(publicFavicon('http://host/icon.png')).toBe('/favicon.ico');
    expect(publicFavicon('https://host/icon.png')).toBe('/favicon.ico');
    expect(publicFavicon('data:image/png;base64,abc')).toBe('/favicon.ico');
    expect(publicFavicon('javascript:alert(1)')).toBe('/favicon.ico');
    expect(publicFavicon('favicon.png')).toBe('/favicon.ico');
  });
});

describe('favicon assets and metadata', () => {
  const publicDirectory = resolve(process.cwd(), 'public');

  it('keeps the favicon assets public and verifies the PNG properties', () => {
    const fallback = readFileSync(resolve(publicDirectory, 'favicon.ico'));
    const favicon = readFileSync(resolve(publicDirectory, 'ultramedical-favicon.png'));

    expect(fallback.length).toBeGreaterThan(0);
    expect(favicon.subarray(0, 8)).toEqual(
      Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    );
    expect(favicon.readUInt32BE(16)).toBe(512);
    expect(favicon.readUInt32BE(20)).toBe(512);
    expect(favicon[25]).toBe(6);
  });

  it('uses the shared branding favicon for every metadata icon type', () => {
    const layout = readFileSync(resolve(process.cwd(), 'src/app/layout.tsx'), 'utf8');

    expect(layout).toContain('icon: publicBranding.faviconUrl');
    expect(layout).toContain('shortcut: publicBranding.faviconUrl');
    expect(layout).toContain('apple: publicBranding.faviconUrl');
  });
});
