const DEFAULT_BRANDING = {
  clinicName: 'CRM Clínico',
  logoUrl: null,
  headline: 'Conecte atendimento, equipe e operação em um só lugar.',
  description: 'Centralize conversas, automações e informações para oferecer um atendimento mais ágil e organizado.',
  benefits: [
    'Atendimentos organizados em tempo real',
    'Automações e integrações em um único fluxo',
    'Dados isolados e acessos por perfil',
  ],
} as const;

const DEFAULT_DOCUMENT_TITLE = 'CRM de Atendimento Clínico';

function publicValue(name: string, fallback: string) {
  const value = process.env[name]?.trim();
  return value || fallback;
}

function publicLogo() {
  const value = process.env.NEXT_PUBLIC_CLINIC_LOGO?.trim();
  return value?.startsWith('/') ? value : DEFAULT_BRANDING.logoUrl;
}

export const publicBranding = {
  clinicName: publicValue('NEXT_PUBLIC_CLINIC_NAME', DEFAULT_BRANDING.clinicName),
  logoUrl: publicLogo(),
  headline: publicValue('NEXT_PUBLIC_LOGIN_HEADLINE', DEFAULT_BRANDING.headline),
  description: publicValue('NEXT_PUBLIC_LOGIN_DESCRIPTION', DEFAULT_BRANDING.description),
  benefits: DEFAULT_BRANDING.benefits,
} as const;

export function buildDocumentTitle(clinicName: string) {
  const normalized = clinicName.trim();
  if (!normalized || normalized.toLocaleLowerCase('pt-BR') === 'crm clínico') {
    return DEFAULT_DOCUMENT_TITLE;
  }
  return normalized.toLocaleLowerCase('pt-BR').startsWith('crm ')
    ? normalized
    : `CRM ${normalized}`;
}

export const publicDocumentTitle = buildDocumentTitle(publicBranding.clinicName);

export function brandingInitials(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => Array.from(part)[0]?.toUpperCase())
    .join('') || 'CRM';
}
