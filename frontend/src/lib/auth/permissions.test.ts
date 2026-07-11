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
      '/minha-conta',
    ]) {
      expect(isRouteAllowed('GESTOR', route)).toBe(true);
    }
  });

  it('should_show_team_management_only_for_authorized_manager', () => {
    expect(menuItemsForProfile('GESTOR', true).map((item) => item.href)).toContain('/equipe');
    expect(menuItemsForProfile('GESTOR').map((item) => item.href)).not.toContain('/equipe');
  });

  it('should_limit_receptionist_to_operational_routes_and_account', () => {
    expect(isRouteAllowed('RECEPCIONISTA', '/pacientes')).toBe(true);
    expect(isRouteAllowed('RECEPCIONISTA', '/horarios')).toBe(true);
    expect(isRouteAllowed('RECEPCIONISTA', '/equipe')).toBe(false);
    expect(isRouteAllowed('RECEPCIONISTA', '/automacao-ia')).toBe(false);
    expect(isRouteAllowed('RECEPCIONISTA', '/configuracoes')).toBe(false);
    expect(isRouteAllowed('RECEPCIONISTA', '/minha-conta')).toBe(true);
  });

  it('should_limit_doctor_to_dashboard_conversations_agenda_and_account', () => {
    expect(menuItemsForProfile('MEDICO').map((item) => item.href)).toEqual([
      '/atendimentos',
      '/dashboard',
      '/agenda',
      '/minha-conta',
    ]);
    expect(isRouteAllowed('MEDICO', '/pacientes')).toBe(false);
    expect(isRouteAllowed('MEDICO', '/configuracoes')).toBe(false);
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

  it('should_show_account_for_all_profiles_and_administration_only_for_manager', () => {
    expect(menuItemsForProfile('GESTOR').map((item) => item.href)).toContain('/configuracoes');
    expect(menuItemsForProfile('GESTOR').map((item) => item.href)).toContain('/minha-conta');

    expect(menuItemsForProfile('RECEPCIONISTA').map((item) => item.href)).not.toContain('/configuracoes');
    expect(menuItemsForProfile('RECEPCIONISTA').map((item) => item.href)).toContain('/minha-conta');

    expect(menuItemsForProfile('MEDICO').map((item) => item.href)).not.toContain('/configuracoes');
    expect(menuItemsForProfile('MEDICO').map((item) => item.href)).toContain('/minha-conta');
  });
});
