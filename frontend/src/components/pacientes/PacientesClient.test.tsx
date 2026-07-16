import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PacientesClient } from './PacientesClient';
import type { TagOperacional } from '@/types/operacional';
import type { PacienteResumo } from '@/types/paciente';

const tags: TagOperacional[] = [
  {
    id: 3,
    nome: 'Prioridade',
    cor: '#ef4444',
    descricao: null,
    ativo: true,
    criadoEm: null,
    atualizadoEm: null,
  },
  {
    id: 4,
    nome: 'Retorno',
    cor: '#0d9488',
    descricao: null,
    ativo: true,
    criadoEm: null,
    atualizadoEm: null,
  },
];

const paciente: PacienteResumo = {
  id: 12,
  nome: 'Maria Silva',
  telefone: '5511999990000',
  status: 'EM_ATENDIMENTO',
  externalSource: 'MEDWARE',
  externalId: 'MW-1',
  criadoEm: '2026-06-15T12:00:00Z',
  ultimaInteracaoEm: null,
  tags: [tags[0]],
};

describe('PacientesClient', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('should_show_and_update_patient_tags', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(tags, 201))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    render(
      <PacientesClient
        initialPacientes={[paciente]}
        availableTags={tags}
        initialError={null}
        canManage
      />,
    );

    expect(screen.getByText('Prioridade')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Adicionar tag para Maria Silva' }));
    await user.click(screen.getByRole('button', { name: 'Adicionar Retorno' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/pacientes/12/tags/4',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(screen.getByText('Retorno')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Remover tag Prioridade de Maria Silva' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/pacientes/12/tags/3',
      expect.objectContaining({ method: 'DELETE' }),
    );
    expect(screen.queryByText('Prioridade')).not.toBeInTheDocument();
  });

  it('should_hide_tag_mutations_when_user_cannot_manage', () => {
    render(
      <PacientesClient
        initialPacientes={[paciente]}
        availableTags={tags}
        initialError={null}
        canManage={false}
      />,
    );

    expect(screen.getByText('Prioridade')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Adicionar tag para Maria Silva' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Remover tag Prioridade de Maria Silva' })).not.toBeInTheDocument();
  });

  it('should_search_by_normalized_name_phone_and_identifiers_without_fetching', async () => {
    const user = userEvent.setup();
    const joao: PacienteResumo = { ...paciente, id: 42, nome: 'Jo\u00e3o  L\u00f3pes', telefone: '(83) 99999-9999', externalId: 'MW-42' };
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    render(<PacientesClient initialPacientes={[paciente, joao]} availableTags={tags} initialError={null} canManage />);

    const search = screen.getByRole('searchbox', { name: 'Buscar pacientes' });
    await user.type(search, 'joao lopes');
    expect(screen.getByText(/Jo\u00e3o/)).toBeInTheDocument();
    expect(screen.queryByText('Maria Silva')).not.toBeInTheDocument();
    expect(screen.getByText('1 de 2 pacientes')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();

    await user.clear(search);
    await user.type(search, '83999999999');
    expect(screen.getByText(/Jo\u00e3o/)).toBeInTheDocument();
    await user.clear(search);
    await user.type(search, 'MW-42');
    expect(screen.getByText(/Jo\u00e3o/)).toBeInTheDocument();
    await user.clear(search);
    await user.type(search, '12');
    expect(screen.getByText('Maria Silva')).toBeInTheDocument();
  });

  it('should_show_search_empty_state_and_keep_tags_actions_after_clearing', async () => {
    const user = userEvent.setup();
    render(<PacientesClient initialPacientes={[paciente]} availableTags={tags} initialError={null} canManage />);
    const search = screen.getByRole('searchbox', { name: 'Buscar pacientes' });
    await user.type(search, 'inexistente');
    expect(screen.getByText(/Nenhum paciente encontrado para/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Limpar pesquisa' }));
    expect(screen.getByRole('button', { name: 'Adicionar tag para Maria Silva' })).toBeInTheDocument();
  });
});

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
