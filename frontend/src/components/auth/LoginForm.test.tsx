import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { LoginForm } from './LoginForm';

const replace = vi.fn();
const refresh = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace, refresh }),
}));

describe('LoginForm', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    replace.mockReset();
    refresh.mockReset();
  });

  it('should_not_offer_public_registration', () => {
    render(<LoginForm />);

    expect(screen.queryByText(/cadastre-se/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/criar conta/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/registrar/i)).not.toBeInTheDocument();
  });

  it('should_keep_accessible_login_fields_and_neutral_placeholder', () => {
    render(<LoginForm />);

    expect(screen.getByLabelText('Email')).toHaveAttribute('autocomplete', 'email');
    expect(screen.getByLabelText('Email')).toHaveAttribute('inputmode', 'email');
    expect(screen.getByLabelText('Email')).toHaveAttribute('placeholder', 'nome@empresa.com');
    expect(screen.getByLabelText('Senha')).toHaveAttribute('autocomplete', 'current-password');
    expect(screen.getByRole('button', { name: 'Entrar' })).toHaveAttribute('type', 'submit');
  });

  it('should_show_backend_error_without_exposing_credentials', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'Credenciais inválidas.' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' },
    })));
    render(<LoginForm />);

    await user.type(screen.getByLabelText('Email'), 'nome@empresa.com');
    await user.type(screen.getByLabelText('Senha'), 'senha-secreta');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Credenciais inválidas.');
    expect(screen.queryByText('senha-secreta')).not.toBeInTheDocument();
  });

  it('should_redirect_to_profile_default_route_after_login', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      user: {
        id: 2,
        nome: 'Recepção',
        email: 'recepcao@clinica.local',
        perfil: 'RECEPCIONISTA',
        clinicaId: 7,
        mustChangePassword: false,
      },
      redirectTo: '/atendimentos',
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);
    render(<LoginForm />);

    await user.type(screen.getByLabelText('Email'), 'recepcao@clinica.local');
    await user.type(screen.getByLabelText('Senha'), 'Senha@123');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/atendimentos'));
    expect(refresh).toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({
      body: JSON.stringify({ email: 'recepcao@clinica.local', senha: 'Senha@123' }),
    }));
  });

  it('should_redirect_initial_password_to_change_password', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      user: {
        id: 3,
        nome: 'Primeiro Acesso',
        email: 'primeiro@clinica.local',
        perfil: 'GESTOR',
        clinicaId: 7,
        mustChangePassword: true,
      },
      redirectTo: '/alterar-senha',
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })));
    render(<LoginForm />);

    await user.type(screen.getByLabelText('Email'), 'primeiro@clinica.local');
    await user.type(screen.getByLabelText('Senha'), 'senha-inicial');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/alterar-senha'));
  });
});
