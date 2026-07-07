import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MinhaContaClient } from './MinhaContaClient';

const user = {
  id: 3,
  nome: 'Dra. Ana Silva',
  email: 'ana@clinica.local',
  perfil: 'MEDICO' as const,
  clinicaId: 7,
  mustChangePassword: false,
};

const clinic = {
  nome: 'UltraMedical',
  slug: 'ultramedical',
  tipoClinica: 'ULTRASSONOGRAFIA' as const,
  usaCirurgiasNaAgenda: true,
  followUpAutomatico: true,
  n8nWebhookConfigurado: true,
};

describe('MinhaContaClient', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should_render_safe_user_account_summary_without_operational_integrations', () => {
    render(<MinhaContaClient user={user} clinic={clinic} />);

    expect(screen.getByRole('heading', { name: 'Minha conta' })).toBeInTheDocument();
    expect(screen.getByText('Dra. Ana Silva')).toBeInTheDocument();
    expect(screen.getByText('ana@clinica.local')).toBeInTheDocument();
    expect(screen.getByText('Médico')).toBeInTheDocument();
    expect(screen.getByText('UltraMedical')).toBeInTheDocument();
    expect(screen.getByText('Agenda')).toBeInTheDocument();
    expect(screen.getByText('Atendimentos')).toBeInTheDocument();
    expect(screen.queryByText(/Medware/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/N8N/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/WhatsApp/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/secret/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/token/i)).not.toBeInTheDocument();
  });

  it('should_show_administrative_permission_only_for_manager', () => {
    const { rerender } = render(<MinhaContaClient user={user} clinic={clinic} />);

    expect(screen.queryByText('Administração do sistema')).not.toBeInTheDocument();

    rerender(
      <MinhaContaClient
        user={{ ...user, perfil: 'GESTOR' }}
        clinic={clinic}
      />,
    );

    expect(screen.getByText('Administração do sistema')).toBeInTheDocument();
  });

  it('should_open_change_password_form_from_security_card', async () => {
    const actor = userEvent.setup();
    render(<MinhaContaClient user={user} clinic={clinic} />);

    await actor.click(screen.getByRole('button', { name: 'Alterar senha' }));

    expect(screen.getByLabelText('Senha atual')).toBeInTheDocument();
    expect(screen.getByLabelText('Nova senha')).toBeInTheDocument();
    expect(screen.getByLabelText('Confirmar nova senha')).toBeInTheDocument();
    expect(screen.getByText('A senha deve ter no mínimo 6 caracteres, contendo letras e números.')).toBeInTheDocument();
  });

  it('should_validate_change_password_fields_before_request', async () => {
    const actor = userEvent.setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(<MinhaContaClient user={user} clinic={clinic} />);

    await actor.click(screen.getByRole('button', { name: 'Alterar senha' }));
    await actor.type(screen.getByLabelText('Senha atual'), 'Atual123');
    await actor.type(screen.getByLabelText('Nova senha'), 'abc@123');
    await actor.type(screen.getByLabelText('Confirmar nova senha'), 'abc@123');
    await actor.click(screen.getByRole('button', { name: 'Salvar nova senha' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('A senha deve ter no mínimo 6 caracteres, contendo letras e números.');
    expect(fetchMock).not.toHaveBeenCalled();

    await actor.clear(screen.getByLabelText('Nova senha'));
    await actor.clear(screen.getByLabelText('Confirmar nova senha'));
    await actor.type(screen.getByLabelText('Nova senha'), 'Lucas123');
    await actor.type(screen.getByLabelText('Confirmar nova senha'), 'Atendente1');
    await actor.click(screen.getByRole('button', { name: 'Salvar nova senha' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('As senhas não coincidem.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('should_show_backend_error_when_current_password_is_wrong', async () => {
    const actor = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      message: 'Senha atual inválida.',
    }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' },
    })));
    render(<MinhaContaClient user={user} clinic={clinic} />);

    await actor.click(screen.getByRole('button', { name: 'Alterar senha' }));
    await actor.type(screen.getByLabelText('Senha atual'), 'Errada123');
    await actor.type(screen.getByLabelText('Nova senha'), 'Lucas123');
    await actor.type(screen.getByLabelText('Confirmar nova senha'), 'Lucas123');
    await actor.click(screen.getByRole('button', { name: 'Salvar nova senha' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Senha atual inválida.');
  });

  it('should_show_success_and_clear_fields_after_password_change', async () => {
    const actor = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      redirectTo: '/dashboard',
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);
    render(<MinhaContaClient user={user} clinic={clinic} />);

    await actor.click(screen.getByRole('button', { name: 'Alterar senha' }));
    await actor.type(screen.getByLabelText('Senha atual'), 'Atual123');
    await actor.type(screen.getByLabelText('Nova senha'), 'Lucas123');
    await actor.type(screen.getByLabelText('Confirmar nova senha'), 'Lucas123');
    await actor.click(screen.getByRole('button', { name: 'Salvar nova senha' }));

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Senha alterada com sucesso.'));
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/change-password', expect.objectContaining({
      method: 'PATCH',
    }));
    expect(screen.getByLabelText('Senha atual')).toHaveValue('');
    expect(screen.getByLabelText('Nova senha')).toHaveValue('');
    expect(screen.getByLabelText('Confirmar nova senha')).toHaveValue('');
  });
});
