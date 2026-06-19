export const AUTH_PROFILES = ['GESTOR', 'RECEPCIONISTA', 'MEDICO'] as const;

export type AuthProfile = (typeof AUTH_PROFILES)[number];

export type AuthUser = {
  id: number;
  nome: string;
  email: string;
  perfil: AuthProfile;
  clinicaId: number;
};

export function isAuthProfile(value: unknown): value is AuthProfile {
  return typeof value === 'string' && AUTH_PROFILES.includes(value as AuthProfile);
}
