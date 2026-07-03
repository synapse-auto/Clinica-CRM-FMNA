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
    await user.type(screen.getByLabelText('Nova senha'), 'Lucas123');
    await user.type(screen.getByLabelText('Confirmar nova senha'), 'Atendente1');
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
    await user.type(screen.getByLabelText('Nova senha'), 'abc123');
    await user.type(screen.getByLabelText('Confirmar nova senha'), 'abc123');
    await user.click(screen.getByRole('button', { name: 'Alterar senha' }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/dashboard'));
    expect(refresh).toHaveBeenCalled();
  });

  it('should_reject_password_under_6_characters_before_request', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(<ChangePasswordForm />);

    await user.type(screen.getByLabelText('Senha atual'), 'SenhaInicial!2026');
    await user.type(screen.getByLabelText('Nova senha'), 'ab12');
    await user.type(screen.getByLabelText('Confirmar nova senha'), 'ab12');
    await user.click(screen.getByRole('button', { name: 'Alterar senha' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('A senha deve ter no mínimo 6 caracteres, contendo letras e números.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('should_accept_valid_crm_password', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      redirectTo: '/dashboard',
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })));
    render(<ChangePasswordForm />);

    await user.type(screen.getByLabelText('Senha atual'), 'SenhaInicial!2026');
    await user.type(screen.getByLabelText('Nova senha'), 'Lucas123');
    await user.type(screen.getByLabelText('Confirmar nova senha'), 'Lucas123');
    await user.click(screen.getByRole('button', { name: 'Alterar senha' }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/dashboard'));
    expect(refresh).toHaveBeenCalled();
  });

  it.each([
    ['123456'],
    ['abcdef'],
    ['abc@123'],
  ])('should_reject_invalid_crm_password_%s_before_request', async (password) => {
    const user = userEvent.setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(<ChangePasswordForm />);

    await user.type(screen.getByLabelText('Senha atual'), 'SenhaInicial!2026');
    await user.type(screen.getByLabelText('Nova senha'), password);
    await user.type(screen.getByLabelText('Confirmar nova senha'), password);
    await user.click(screen.getByRole('button', { name: 'Alterar senha' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('A senha deve ter no mínimo 6 caracteres, contendo letras e números.');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
