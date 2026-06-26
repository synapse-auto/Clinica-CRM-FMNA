import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ChangePasswordForm } from './ChangePasswordForm';

const replace = vi.fn();
const refresh = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace, refresh }),
}));

describe('ChangePasswordForm', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    replace.mockReset();
    refresh.mockReset();
  });

  it('should_reject_mismatched_password_confirmation_before_request', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(<ChangePasswordForm />);

    await user.type(screen.getByLabelText('Senha atual'), 'SenhaInicial!2026');
    await user.type(screen.getByLabelText('Nova senha'), 'NovaSenhaSegura!2026');
    await user.type(screen.getByLabelText('Confirmar nova senha'), 'OutraSenhaSegura!2026');
    await user.click(screen.getByRole('button', { name: 'Alterar senha' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('As senhas não coincidem.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('should_redirect_after_successful_password_change', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      redirectTo: '/dashboard',
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })));
    render(<ChangePasswordForm />);

    await user.type(screen.getByLabelText('Senha atual'), 'SenhaInicial!2026');
    await user.type(screen.getByLabelText('Nova senha'), 'NovaSenhaSegura!2026');
    await user.type(screen.getByLabelText('Confirmar nova senha'), 'NovaSenhaSegura!2026');
    await user.click(screen.getByRole('button', { name: 'Alterar senha' }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/dashboard'));
    expect(refresh).toHaveBeenCalled();
  });

  it('should_reject_password_under_8_characters_before_request', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(<ChangePasswordForm />);

    await user.type(screen.getByLabelText('Senha atual'), 'SenhaInicial!2026');
    await user.type(screen.getByLabelText('Nova senha'), '12345');
    await user.type(screen.getByLabelText('Confirmar nova senha'), '12345');
    await user.click(screen.getByRole('button', { name: 'Alterar senha' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('A senha deve ter pelo menos 8 caracteres.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('should_accept_8_character_simple_password', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      redirectTo: '/dashboard',
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })));
    render(<ChangePasswordForm />);

    await user.type(screen.getByLabelText('Senha atual'), 'SenhaInicial!2026');
    await user.type(screen.getByLabelText('Nova senha'), '12345678');
    await user.type(screen.getByLabelText('Confirmar nova senha'), '12345678');
    await user.click(screen.getByRole('button', { name: 'Alterar senha' }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/dashboard'));
    expect(refresh).toHaveBeenCalled();
  });
});
