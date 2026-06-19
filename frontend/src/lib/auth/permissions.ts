import type { AuthProfile } from './types';

export type AuthMenuItem = {
  name: string;
  href: string;
  badge?: number;
  profiles: readonly AuthProfile[];
};

const ALL_PROFILES: readonly AuthProfile[] = ['GESTOR', 'RECEPCIONISTA', 'MEDICO'];
const OPERATIONAL_PROFILES: readonly AuthProfile[] = ['GESTOR', 'RECEPCIONISTA'];

export const AUTH_MENU_ITEMS: readonly AuthMenuItem[] = [
  { name: 'Atendimentos', href: '/atendimentos', badge: 4, profiles: ALL_PROFILES },
  { name: 'Dashboard', href: '/dashboard', profiles: ALL_PROFILES },
  { name: 'Agenda', href: '/agenda', profiles: ALL_PROFILES },
  { name: 'Pacientes', href: '/pacientes', profiles: OPERATIONAL_PROFILES },
  { name: 'Equipe', href: '/equipe', profiles: ['GESTOR'] },
  { name: 'Automação', href: '/automacao-ia', profiles: ['GESTOR'] },
  { name: 'Tags', href: '/tags', profiles: OPERATIONAL_PROFILES },
  { name: 'Msgs Rápidas', href: '/msgs-rapidas', profiles: OPERATIONAL_PROFILES },
  { name: 'Horários', href: '/horarios', profiles: OPERATIONAL_PROFILES },
  { name: 'Configurações', href: '/configuracoes', profiles: ['GESTOR'] },
];

export function menuItemsForProfile(profile: AuthProfile): AuthMenuItem[] {
  return AUTH_MENU_ITEMS.filter((item) => item.profiles.includes(profile));
}

export function isRouteAllowed(profile: AuthProfile, pathname: string): boolean {
  const item = AUTH_MENU_ITEMS.find(
    (candidate) => pathname === candidate.href || pathname.startsWith(`${candidate.href}/`),
  );
  return item ? item.profiles.includes(profile) : false;
}

export function defaultRouteForProfile(profile: AuthProfile): string {
  if (profile === 'GESTOR') return '/dashboard';
  if (profile === 'RECEPCIONISTA') return '/atendimentos';
  return '/agenda';
}
