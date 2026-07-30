import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { act } from 'react';
import { beforeEach, vi } from 'vitest';
import { DemoSidebar } from './DemoSidebar';

const replaceMock = vi.fn();
const refreshMock = vi.fn();
const toggleThemeMock = vi.fn();
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
    toggleTheme: toggleThemeMock,
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
    toggleThemeMock.mockClear();
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

  it('should_keep_a_fixed_64px_rail_that_expands_on_hover_focus_without_a_pin_control', () => {
    navigation.pathname = '/atendimentos';
    render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false, podeGerenciarUsuarios: false }}
      />,
    );

    const rail = screen.getByTestId('sidebar-rail');
    const sidebar = screen.getByTestId('main-sidebar');
    // Trilho sempre compacto (64px) no desktop, largura total (256px) no mobile.
    expect(rail).toHaveClass('w-[256px]', 'md:w-16');
    // Expansão temporária como overlay por hover/focus, sem mexer no trilho.
    expect(sidebar).toHaveClass('absolute', 'z-40', 'md:w-16', 'md:hover:w-[256px]');
    expect(sidebar).not.toHaveClass('md:focus-within:w-[256px]');
    // Ícones sempre presentes; labels só aparecem no hover/focus (opacity-0 -> 100).
    expect(screen.getByRole('link', { name: /Atendimentos/ })).toBeInTheDocument();
    expect(screen.getByText('Atendimentos')).toHaveClass(
      'opacity-0',
      'group-hover/sidebar:opacity-100',
    );
    // Sem botão de fixar/recolher.
    expect(screen.queryByRole('button', { name: /Fixar/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Recolher/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /barra lateral/i })).not.toBeInTheDocument();
  });

  it('should_not_persist_any_sidebar_preference_in_localStorage', async () => {
    navigation.pathname = '/atendimentos';
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    const user = userEvent.setup();
    render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false, podeGerenciarUsuarios: false }}
      />,
    );

    await user.hover(screen.getByTestId('main-sidebar'));

    expect(window.localStorage.getItem('clinica-crm-atendimentos-sidebar-expanded')).toBeNull();
    expect(setItem).not.toHaveBeenCalledWith(
      'clinica-crm-atendimentos-sidebar-expanded',
      expect.anything(),
    );
    setItem.mockRestore();
  });

  it('should_render_a_full_256px_sidebar_with_visible_labels_on_other_routes', () => {
    navigation.pathname = '/dashboard';
    render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false, podeGerenciarUsuarios: false }}
      />,
    );

    const rail = screen.getByTestId('sidebar-rail');
    const sidebar = screen.getByTestId('main-sidebar');
    expect(rail).toHaveClass('w-[256px]', 'md:w-16');
    expect(sidebar).toHaveClass('md:hover:w-[256px]');
    expect(sidebar).not.toHaveClass('md:focus-within:w-[256px]');
    // Em todas as rotas, labels são reveladas apenas no hover ou foco no desktop.
    expect(screen.getByText('Atendimentos')).toHaveClass('opacity-0', 'group-hover/sidebar:opacity-100');
  });

  it('should_keep_the_rail_width_and_update_the_active_item_after_navigation', () => {
    const sidebarUser = { id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR' as const, clinicaId: 7, mustChangePassword: false, podeGerenciarUsuarios: false };
    const { rerender } = render(<DemoSidebar clinic={clinic} user={sidebarUser} />);

    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveClass('bg-sidebar-accent');

    navigation.pathname = '/pacientes';
    rerender(<DemoSidebar clinic={clinic} user={sidebarUser} />);

    expect(screen.getByTestId('sidebar-rail')).toHaveClass('md:w-16');
    expect(screen.getByRole('link', { name: 'Pacientes' })).toHaveClass('bg-sidebar-accent');
  });

  it('should_not_expand_for_a_link_focused_after_pointer_interaction', () => {
    render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false, podeGerenciarUsuarios: true }}
      />,
    );

    const sidebar = screen.getByTestId('main-sidebar');
    const equipeLink = screen.getByRole('link', { name: 'Equipe' });
    fireEvent.pointerDown(equipeLink, { pointerType: 'mouse' });
    fireEvent.focus(equipeLink);
    fireEvent.pointerLeave(sidebar, { pointerType: 'mouse' });

    expect(sidebar).toHaveAttribute('data-keyboard-expanded', 'false');
    expect(sidebar).toHaveClass('md:w-16');
    expect(sidebar).not.toHaveClass('md:w-[256px]');
  });

  it.each([
    ['tema', () => screen.getByRole('button', { name: /Ativar tema claro/i })],
    ['minha conta', () => screen.getAllByTitle('Minha conta').at(-1)!],
    ['sair', () => screen.getByRole('button', { name: 'Sair' })],
  ])('should_not_keep_keyboard_expansion_after_pointer_interaction_with_%s', (_, getControl) => {
    render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false, podeGerenciarUsuarios: false }}
      />,
    );

    const sidebar = screen.getByTestId('main-sidebar');
    const keyboardLink = screen.getByRole('link', { name: 'Dashboard' });
    fireEvent.keyDown(window, { key: 'Tab' });
    fireEvent.focus(keyboardLink);
    expect(sidebar).toHaveAttribute('data-keyboard-expanded', 'true');

    const control = getControl();
    fireEvent.pointerDown(control, { pointerType: 'mouse' });
    fireEvent.focus(control);

    expect(sidebar).toHaveAttribute('data-keyboard-expanded', 'false');
  });

  it('should_expand_for_keyboard_focus_and_remain_expanded_between_internal_links', () => {
    render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false, podeGerenciarUsuarios: false }}
      />,
    );

    const sidebar = screen.getByTestId('main-sidebar');
    const dashboardLink = screen.getByRole('link', { name: 'Dashboard' });
    const pacientesLink = screen.getByRole('link', { name: 'Pacientes' });
    fireEvent.keyDown(window, { key: 'Tab' });
    fireEvent.focus(dashboardLink);

    expect(sidebar).toHaveAttribute('data-keyboard-expanded', 'true');
    expect(sidebar).toHaveClass('md:w-[256px]');
    expect(screen.getByText('Dashboard')).toHaveClass('opacity-100');

    fireEvent.blur(dashboardLink, { relatedTarget: pacientesLink });
    fireEvent.focus(pacientesLink);

    expect(sidebar).toHaveAttribute('data-keyboard-expanded', 'true');
  });

  it('should_collapse_when_keyboard_focus_leaves_the_sidebar', () => {
    render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false, podeGerenciarUsuarios: false }}
      />,
    );

    const sidebar = screen.getByTestId('main-sidebar');
    const dashboardLink = screen.getByRole('link', { name: 'Dashboard' });
    fireEvent.keyDown(window, { key: 'Tab', shiftKey: true });
    fireEvent.focus(dashboardLink);
    fireEvent.keyDown(dashboardLink, { key: 'Enter' });
    fireEvent.blur(dashboardLink, { relatedTarget: document.body });

    expect(sidebar).toHaveAttribute('data-keyboard-expanded', 'false');
    expect(sidebar).toHaveClass('md:w-16');
  });

  it('should_move_the_badge_for_keyboard_expansion_and_restore_it_after_blur', () => {
    render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false, podeGerenciarUsuarios: false }}
      />,
    );

    act(() => {
      window.dispatchEvent(new CustomEvent('atendimentos:badge', { detail: 3 }));
    });
    const dashboardLink = screen.getByRole('link', { name: 'Dashboard' });
    const badge = screen.getByText('3');
    fireEvent.keyDown(window, { key: 'Tab' });
    fireEvent.focus(dashboardLink);

    expect(badge).toHaveClass('md:right-3', 'md:left-auto');

    fireEvent.blur(dashboardLink, { relatedTarget: document.body });

    expect(badge).toHaveClass('md:left-8', 'md:right-auto');
  });

  it.each(['/pacientes', '/equipe'])(
    'should_keep_the_compact_rail_on_%s',
    (pathname) => {
      navigation.pathname = pathname;
      render(
        <DemoSidebar
          clinic={clinic}
          user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false, podeGerenciarUsuarios: false }}
        />,
      );

      expect(screen.getByTestId('sidebar-rail')).toHaveClass('md:w-16');
      expect(screen.getByTestId('main-sidebar')).toHaveClass('md:hover:w-[256px]');
    },
  );

  it('should_keep_titles_badge_and_theme_control_accessible_in_the_compact_rail', async () => {
    const user = userEvent.setup();
    render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false, podeGerenciarUsuarios: false }}
      />,
    );

    act(() => {
      window.dispatchEvent(new CustomEvent('atendimentos:badge', { detail: 3 }));
    });

    expect(screen.getByTitle('Atendimentos')).toBeInTheDocument();
    expect(screen.getAllByTitle('Minha conta')).not.toHaveLength(0);
    expect(screen.getByText('3')).toHaveClass('md:left-8', 'md:group-hover/sidebar:right-3');

    await user.click(screen.getByRole('button', { name: /Ativar tema claro/i }));

    expect(toggleThemeMock).toHaveBeenCalledOnce();
  });

  it('should_not_depend_on_hover_for_the_mobile_layout_on_atendimentos', () => {
    navigation.pathname = '/atendimentos';
    render(
      <DemoSidebar
        clinic={clinic}
        user={{ id: 1, nome: 'Gestora', email: 'gestora@clinica.local', perfil: 'GESTOR', clinicaId: 7, mustChangePassword: false, podeGerenciarUsuarios: false }}
      />,
    );

    // No mobile o overlay vira layout estático e os labels ficam sempre visíveis.
    expect(screen.getByTestId('main-sidebar')).toHaveClass('max-md:static');
    expect(screen.getByText('Atendimentos')).toHaveClass('max-md:opacity-100');
  });
});
