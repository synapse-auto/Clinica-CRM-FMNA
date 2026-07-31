import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  buildDocumentTitle,
  publicFavicon,
  publicLogo,
  publicLogoBorderRadius,
} from './public-branding';

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
    expect(publicFavicon('/fmna-favicon.png')).toBe('/fmna-favicon.png');
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

describe('publicLogo', () => {
  it('selects each clinic logo from a local public path', () => {
    expect(publicLogo('/fmna-logo.png')).toBe('/fmna-logo.png');
    expect(publicLogo('/ultramedical-logo.png')).toBe('/ultramedical-logo.png');
  });

  it('does not accept external or protocol-relative logo paths', () => {
    expect(publicLogo()).toBeNull();
    expect(publicLogo('//host/logo.png')).toBeNull();
    expect(publicLogo('https://host/logo.png')).toBeNull();
  });
});

describe('publicLogoBorderRadius', () => {
  it.each([
    ['12', 12],
    ['0', 0],
    [undefined, 0],
    ['-5', 0],
    ['abc', 0],
    ['999', 64],
  ])('normalizes %s to %s pixels', (value, expected) => {
    expect(publicLogoBorderRadius(value)).toBe(expected);
  });
});

describe('favicon assets and metadata', () => {
  const publicDirectory = resolve(process.cwd(), 'public');

  it('keeps the clinic assets public and verifies the PNG properties', () => {
    const fallback = readFileSync(resolve(publicDirectory, 'favicon.ico'));
    const ultramedicalFavicon = readFileSync(resolve(publicDirectory, 'ultramedical-favicon.png'));
    const ultramedicalLogo = readFileSync(resolve(publicDirectory, 'ultramedical-logo.png'));
    const fmnaFavicon = readFileSync(resolve(publicDirectory, 'fmna-favicon.png'));
    const fmnaLogo = readFileSync(resolve(publicDirectory, 'fmna-logo.png'));

    expect(fallback.length).toBeGreaterThan(0);
    for (const asset of [ultramedicalFavicon, ultramedicalLogo, fmnaFavicon, fmnaLogo]) {
      expect(asset.subarray(0, 8)).toEqual(
        Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
      );
    }
    expect(ultramedicalFavicon.readUInt32BE(16)).toBe(512);
    expect(ultramedicalFavicon.readUInt32BE(20)).toBe(512);
    expect(fmnaFavicon.readUInt32BE(16)).toBe(512);
    expect(fmnaFavicon.readUInt32BE(20)).toBe(512);
    expect(fmnaLogo.readUInt32BE(16)).toBe(1024);
    expect(fmnaLogo.readUInt32BE(20)).toBe(1024);
  });

  it('uses the shared branding favicon for every metadata icon type', () => {
    const layout = readFileSync(resolve(process.cwd(), 'src/app/layout.tsx'), 'utf8');

    expect(layout).toContain('icon: publicBranding.faviconUrl');
    expect(layout).toContain('shortcut: publicBranding.faviconUrl');
    expect(layout).toContain('apple: publicBranding.faviconUrl');
  });

  it('applies the shared logo radius in login and sidebar without changing favicon metadata', () => {
    const login = readFileSync(resolve(process.cwd(), 'src/app/login/page.tsx'), 'utf8');
    const sidebar = readFileSync(resolve(process.cwd(), 'src/components/demo/DemoSidebar.tsx'), 'utf8');

    for (const component of [login, sidebar]) {
      expect(component).toContain('<BrandLogo');
      expect(component).toContain('borderRadius={publicBranding.logoBorderRadius}');
      expect(component).not.toContain("clinicName === 'FMNA'");
    }
  });
});
