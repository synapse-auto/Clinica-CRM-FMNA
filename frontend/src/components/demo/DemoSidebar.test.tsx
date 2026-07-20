import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, vi } from 'vitest';
import { DemoSidebar } from './DemoSidebar';

const replaceMock = vi.fn();
const refreshMock = vi.fn();
const navigation = vi.hoisted(() => ({ pathname: '/dashboard' }));

vi.mock('next/navigation', () => ({
  usePathname: () => navigation.pathname,
  useRouter: () => ({
    replace: replaceMock,
    refresh: refreshMock,
  }),
}));

vi.mock('@/components/theme/ThemeProvider', () => ({
  useTheme: () => ({
    theme: 'dark',
    toggleTheme: vi.fn(),
  }),
}));

const clinic = {
  nome: 'Clinica Femina',
  slug: 'clinica-femina',
  tipoClinica: 'PRE_NATAL' as const,
  usaCirurgiasNaAgenda: true,
  followUpAutomatico: true,
};

describe('DemoSidebar', () => {
  beforeEach(() => {
    navigation.pathname = '/dashboard';
    window.localStorage.clear();
  });

  it('should_not_render_whatsapp_demo_shortcuts', () => {
    render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false }}
      />,
    );

    expect(screen.queryByText('WhatsApp IA')).not.toBeInTheDocument();
    expect(screen.queryByText('WhatsApp Integrado')).not.toBeInTheDocument();
    expect(screen.queryByText('WhatsApp Demo')).not.toBeInTheDocument();
  });

  it('should_hide_manager_and_operational_routes_from_doctor', () => {
    render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 3, nome: 'Dra. Ana', email: 'medica@clinica.local', perfil: 'MEDICO', clinicaId: 7, mustChangePassword: false }}
      />,
    );

    expect(screen.getByText('Agenda')).toBeInTheDocument();
    expect(screen.getByText('Minha conta')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Dra. Ana/ })).toHaveAttribute('href', '/minha-conta');
    expect(screen.queryByText('Pacientes')).not.toBeInTheDocument();
    expect(screen.queryByText('Equipe')).not.toBeInTheDocument();
    expect(screen.queryByText('Administração')).not.toBeInTheDocument();
  });

  it('should_show_administration_only_for_manager_and_account_for_all_profiles', () => {
    const { rerender } = render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false }}
      />,
    );

    expect(screen.getByText('Administração')).toBeInTheDocument();
    expect(screen.getByText('Minha conta')).toBeInTheDocument();

    rerender(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 2, nome: 'Recepcao', email: 'recepcao@clinica.local', perfil: 'RECEPCIONISTA', clinicaId: 7, mustChangePassword: false }}
      />,
    );

    expect(screen.queryByText('Administração')).not.toBeInTheDocument();
    expect(screen.getByText('Minha conta')).toBeInTheDocument();
  });

  it('should_logout_and_redirect_to_login', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false }}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Sair' }));

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith('/login'));
    expect(refreshMock).toHaveBeenCalled();
  });

  it('should_render_branding_fallback_without_a_white_logo_container', () => {
    render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false }}
      />,
    );

    expect(screen.getByLabelText('Clinica Femina sem logotipo')).toBeInTheDocument();
    expect(screen.queryByAltText('UltraMedical')).not.toBeInTheDocument();
  });

  it('should_use_a_fixed_compact_rail_and_allow_pinning_on_atendimentos', async () => {
    navigation.pathname = '/atendimentos';
    const user = userEvent.setup();
    render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false }}
      />,
    );

    const rail = screen.getByTestId('sidebar-rail');
    const sidebar = screen.getByTestId('main-sidebar');
    await waitFor(() => expect(rail).toHaveClass('md:w-16'));
    expect(rail).toHaveClass('w-[256px]');
    expect(sidebar).toHaveClass('md:hover:w-[256px]', 'md:focus-within:w-[256px]');
    expect(screen.getByText('Atendimentos')).toHaveClass('opacity-0', 'group-hover/sidebar:opacity-100');

    const pin = screen.getByRole('button', { name: 'Fixar barra lateral expandida' });
    expect(pin).toHaveAttribute('aria-pressed', 'false');
    await user.click(pin);

    expect(rail).toHaveClass('w-[256px]');
    expect(screen.getByRole('button', { name: 'Recolher barra lateral' })).toHaveAttribute('aria-pressed', 'true');
    expect(window.localStorage.getItem('clinica-crm-atendimentos-sidebar-expanded')).toBe('true');
  });
});
