import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ThemeProvider } from '@/components/theme/ThemeProvider';
import { AppShell } from './AppShell';

vi.mock('next/navigation', () => ({
  usePathname: () => '/dashboard',
  useRouter: () => ({ replace: vi.fn(), refresh: vi.fn() }),
}));

const clinic = {
  nome: 'UltraMedical',
  slug: 'ultramedical',
  tipoClinica: 'ULTRASSONOGRAFIA' as const,
  usaCirurgiasNaAgenda: true,
  followUpAutomatico: true,
  n8nWebhookConfigurado: true,
};

const user = {
  id: 1,
  nome: 'Gestor Teste',
  email: 'gestor@clinica.local',
  perfil: 'GESTOR' as const,
  clinicaId: 1,
  mustChangePassword: false,
};

describe('AppShell', () => {
  it('should_not_apply_comfortable_scale_class_so_crm_renders_at_100_percent', () => {
    render(
      <ThemeProvider>
        <AppShell clinic={clinic} user={user}>
          <main>Conteúdo</main>
        </AppShell>
      </ThemeProvider>,
    );

    const shell = screen.getByTestId('app-shell');
    expect(shell).not.toHaveClass('app-scale-comfortable');
  });
});
