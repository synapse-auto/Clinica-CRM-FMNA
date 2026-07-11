import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AcessosClient } from './AcessosClient';
import type { AuthUser } from '@/lib/auth/types';

const currentUser: AuthUser = {
  id: 1,
  nome: 'Lucas Admin',
  email: 'lucas@ultramedical.local',
  perfil: 'GESTOR',
  clinicaId: 10,
  mustChangePassword: false,
  podeGerenciarUsuarios: true,
};

const mockUsuarios = [
  {
    id: 1,
    nome: 'Lucas Admin',
    email: 'lucas@ultramedical.local',
    telefone: '44999999999',
    perfil: 'GESTOR',
    ativo: true,
    mustChangePassword: false,
    criadoEm: '2026-06-29T12:00:00Z',
  },
  {
    id: 2,
    nome: 'Medico Teste',
    email: 'medico@clinica.local',
    telefone: null,
    perfil: 'MEDICO',
    ativo: true,
    mustChangePassword: true,
    criadoEm: '2026-06-29T12:00:00Z',
  },
];

describe('AcessosClient', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('should_render_user_table_with_details', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(mockUsuarios), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);

    render(<AcessosClient user={currentUser} />);

    // Wait for the loading state to finish
    await waitFor(() => expect(screen.getByText('Lucas Admin')).toBeInTheDocument());

    expect(screen.getByText('Medico Teste')).toBeInTheDocument();
    expect(screen.getByText('lucas@ultramedical.local')).toBeInTheDocument();
    expect(screen.getByText('medico@clinica.local')).toBeInTheDocument();
    expect(screen.getAllByText('Ativo')).toHaveLength(2);
  });

  it('should_create_user_successfully', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockImplementation((url, init) => {
      if (url === '/api/admin/usuarios' && init?.method === 'POST') {
        return Promise.resolve(new Response(JSON.stringify({
          id: 3,
          nome: 'Novo Recepcionista',
          email: 'recepcao@clinica.local',
          telefone: '44988887777',
          perfil: 'RECEPCIONISTA',
          ativo: true,
          mustChangePassword: true,
          criadoEm: '2026-06-29T12:00:00Z',
        }), {
          status: 201,
          headers: { 'Content-Type': 'application/json' },
        }));
      }
      return Promise.resolve(new Response(JSON.stringify(mockUsuarios), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }));
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<AcessosClient user={currentUser} />);

    await waitFor(() => expect(screen.getByText('Lucas Admin')).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: /novo usuário/i }));

    await user.type(screen.getByLabelText('Nome'), 'Novo Recepcionista');
    await user.type(screen.getByLabelText('Email'), 'recepcao@clinica.local');
    await user.selectOptions(screen.getByLabelText('Perfil'), 'RECEPCIONISTA');
    await user.type(screen.getByLabelText('Telefone'), '44988887777');
    await user.type(screen.getByLabelText('Senha temporária'), 'Recepcao123');

    await user.click(screen.getByRole('button', { name: 'Criar usuário' }));

    await waitFor(() => expect(screen.getByText('Novo Recepcionista')).toBeInTheDocument());
  });

  it('should_toggle_user_status_with_confirmation', async () => {
    const user = userEvent.setup();
    const confirmMock = vi.stubGlobal('confirm', () => true);

    const fetchMock = vi.fn().mockImplementation((url, init) => {
      if (url === '/api/admin/usuarios/2/status' && init?.method === 'PATCH') {
        return Promise.resolve(new Response(JSON.stringify({
          ...mockUsuarios[1],
          ativo: false,
        }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }));
      }
      return Promise.resolve(new Response(JSON.stringify(mockUsuarios), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }));
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<AcessosClient user={currentUser} />);

    await waitFor(() => expect(screen.getByText('Medico Teste')).toBeInTheDocument());

    // Click the toggle status button for user 2 (Medico Teste)
    const deactivateBtn = screen.getByTitle('Desativar acesso');
    await user.click(deactivateBtn);

    expect(fetchMock).toHaveBeenCalledWith('/api/admin/usuarios/2/status', expect.objectContaining({
      method: 'PATCH',
      body: JSON.stringify({ ativo: false }),
    }));
  });

  it('should_reset_user_password_successfully', async () => {
    const user = userEvent.setup();
    const alertMock = vi.stubGlobal('alert', () => {});

    const fetchMock = vi.fn().mockImplementation((url, init) => {
      if (url === '/api/admin/usuarios/2/resetar-senha' && init?.method === 'PATCH') {
        return Promise.resolve(new Response(JSON.stringify({
          ...mockUsuarios[1],
          mustChangePassword: true,
        }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }));
      }
      return Promise.resolve(new Response(JSON.stringify(mockUsuarios), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }));
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<AcessosClient user={currentUser} />);

    await waitFor(() => expect(screen.getByText('Medico Teste')).toBeInTheDocument());

    const resetBtn = screen.getAllByTitle('Redefinir senha temporária')[1];
    await user.click(resetBtn);

    await user.type(screen.getByLabelText('Nova Senha Temporária'), 'NovaSenha123');
    await user.click(screen.getByRole('button', { name: 'Confirmar' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith('/api/admin/usuarios/2/resetar-senha', expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ senhaTemporaria: 'NovaSenha123' }),
      }));
    });
  });
});
