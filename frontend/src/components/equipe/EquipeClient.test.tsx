import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { EquipeClient } from './EquipeClient';
import type { EquipeResponse } from '@/types/equipe';

const emptyTeam: EquipeResponse = {
  grupos: [
    { perfil: 'GESTOR', titulo: 'Gestores', usuarios: [] },
    { perfil: 'MEDICO', titulo: 'Médicos', usuarios: [] },
    { perfil: 'RECEPCIONISTA', titulo: 'Recepcionistas', usuarios: [] },
  ],
};

const realTeam: EquipeResponse = {
  grupos: [
    {
      perfil: 'GESTOR',
      titulo: 'Gestores',
      usuarios: [{
        id: 1,
        nome: 'Gestora Real',
        email: 'gestora@clinica.local',
        telefone: '44999999999',
        perfil: 'GESTOR',
        ativo: true,
        mustChangePassword: false,
        criadoEm: '2026-06-29T12:00:00Z',
      }],
    },
    {
      perfil: 'MEDICO',
      titulo: 'Médicos',
      usuarios: [{
        id: 2,
        nome: 'Medico Real',
        email: 'medico@clinica.local',
        telefone: null,
        perfil: 'MEDICO',
        ativo: true,
        mustChangePassword: true,
        criadoEm: '2026-06-29T12:00:00Z',
      }],
    },
    {
      perfil: 'RECEPCIONISTA',
      titulo: 'Recepcionistas',
      usuarios: [],
    },
  ],
};

describe('EquipeClient', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('should_render_real_users_without_fake_team_metrics', () => {
    render(<EquipeClient initialData={realTeam} initialError={null} />);

    expect(screen.getByText('Gestora Real')).toBeInTheDocument();
    expect(screen.getByText('gestora@clinica.local')).toBeInTheDocument();
    expect(screen.getByText('Medico Real')).toBeInTheDocument();
    expect(screen.queryByText('Dra. Renata Fiuza')).not.toBeInTheDocument();
    expect(screen.queryByText('Dr. Carlos Mendes')).not.toBeInTheDocument();
    expect(screen.queryByText('Ana Lima')).not.toBeInTheDocument();
    expect(screen.queryByText('Online')).not.toBeInTheDocument();
    expect(screen.queryByText('14 ativos')).not.toBeInTheDocument();
    expect(screen.queryByText('3,2min médio')).not.toBeInTheDocument();
  });

  it('should_show_empty_state_when_team_has_no_users', () => {
    render(<EquipeClient initialData={emptyTeam} initialError={null} />);

    expect(screen.getByText('Nenhum usuário cadastrado')).toBeInTheDocument();
    expect(screen.getByText(/adicione gestores, médicos ou recepcionistas reais/i)).toBeInTheDocument();
  });

  it('should_open_dialog_and_create_receptionist_using_bff', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 10,
      nome: 'Recepcao Nova',
      email: 'recepcao.nova@clinica.local',
      telefone: '44988887777',
      perfil: 'RECEPCIONISTA',
      ativo: true,
      mustChangePassword: true,
      criadoEm: '2026-06-29T12:00:00Z',
    }), {
      status: 201,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);
    render(<EquipeClient initialData={emptyTeam} initialError={null} />);

    await user.click(screen.getByRole('button', { name: /novo usuário/i }));
    await user.type(screen.getByLabelText('Nome'), 'Recepcao Nova');
    await user.type(screen.getByLabelText('Email'), 'recepcao.nova@clinica.local');
    await user.selectOptions(screen.getByLabelText('Perfil'), 'RECEPCIONISTA');
    await user.type(screen.getByLabelText('Telefone'), '44988887777');
    await user.type(screen.getByLabelText('Senha temporária'), 'Acesso!123');
    await user.click(screen.getByRole('button', { name: 'Criar usuário' }));

    await waitFor(() => expect(screen.getByText('Recepcao Nova')).toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledWith('/api/equipe/usuarios', expect.objectContaining({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        nome: 'Recepcao Nova',
        email: 'recepcao.nova@clinica.local',
        perfil: 'RECEPCIONISTA',
        telefone: '44988887777',
        senhaTemporaria: 'Acesso!123',
      }),
    }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should_show_backend_error_when_create_user_fails', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      message: 'Email já cadastrado.',
    }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' },
    })));
    render(<EquipeClient initialData={emptyTeam} initialError={null} />);

    await user.click(screen.getByRole('button', { name: /novo usuário/i }));
    await user.type(screen.getByLabelText('Nome'), 'Recepcao Nova');
    await user.type(screen.getByLabelText('Email'), 'recepcao.nova@clinica.local');
    await user.selectOptions(screen.getByLabelText('Perfil'), 'RECEPCIONISTA');
    await user.type(screen.getByLabelText('Senha temporária'), 'Atendente1');
    await user.click(screen.getByRole('button', { name: 'Criar usuário' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Email já cadastrado.');
  });
});
