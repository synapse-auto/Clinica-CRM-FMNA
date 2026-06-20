import { describe, expect, it } from 'vitest';
import {
  defaultRouteForProfile,
  isRouteAllowed,
  menuItemsForProfile,
  routeAfterAuthentication,
} from './permissions';

describe('auth permissions', () => {
  it('should_allow_all_internal_routes_for_manager', () => {
    for (const route of [
      '/dashboard',
      '/atendimentos',
      '/agenda',
      '/pacientes',
      '/tags',
      '/msgs-rapidas',
      '/horarios',
      '/equipe',
      '/automacao-ia',
      '/configuracoes',
    ]) {
      expect(isRouteAllowed('GESTOR', route)).toBe(true);
    }
  });

  it('should_limit_receptionist_to_operational_routes', () => {
    expect(isRouteAllowed('RECEPCIONISTA', '/pacientes')).toBe(true);
    expect(isRouteAllowed('RECEPCIONISTA', '/horarios')).toBe(true);
    expect(isRouteAllowed('RECEPCIONISTA', '/equipe')).toBe(false);
    expect(isRouteAllowed('RECEPCIONISTA', '/automacao-ia')).toBe(false);
    expect(isRouteAllowed('RECEPCIONISTA', '/configuracoes')).toBe(false);
  });

  it('should_limit_doctor_to_dashboard_conversations_and_agenda', () => {
    expect(menuItemsForProfile('MEDICO').map((item) => item.href)).toEqual([
      '/atendimentos',
      '/dashboard',
      '/agenda',
    ]);
    expect(isRouteAllowed('MEDICO', '/pacientes')).toBe(false);
  });

  it('should_choose_an_allowed_default_route_for_each_profile', () => {
    expect(defaultRouteForProfile('GESTOR')).toBe('/dashboard');
    expect(defaultRouteForProfile('RECEPCIONISTA')).toBe('/atendimentos');
    expect(defaultRouteForProfile('MEDICO')).toBe('/agenda');
  });

  it('should_force_password_change_before_profile_default_route', () => {
    expect(routeAfterAuthentication('GESTOR', true)).toBe('/alterar-senha');
    expect(routeAfterAuthentication('MEDICO', false)).toBe('/agenda');
  });
});
