import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PacientesClient } from './PacientesClient';
import type { TagOperacional } from '@/types/operacional';
import type { PacientePage, PacienteResumo } from '@/types/paciente';

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
        initialPage={pageWith([paciente])}
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
        initialPage={pageWith([paciente])}
        availableTags={tags}
        initialError={null}
        canManage={false}
      />,
    );

    expect(screen.getByText('Prioridade')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Adicionar tag para Maria Silva' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Remover tag Prioridade de Maria Silva' })).not.toBeInTheDocument();
  });

  it('should_search_server_side_by_normalized_name_and_preserve_current_results_while_loading', async () => {
    const user = userEvent.setup();
    const joao: PacienteResumo = { ...paciente, id: 42, nome: 'Jo\u00e3o  L\u00f3pes', telefone: '(83) 99999-9999', externalId: 'MW-42' };
    let resolveSearch: ((response: Response) => void) | undefined;
    const fetchMock = vi.fn(() => new Promise<Response>((resolve) => {
      resolveSearch = resolve;
    }));
    vi.stubGlobal('fetch', fetchMock);
    render(<PacientesClient initialPage={pageWith([paciente, joao])} availableTags={tags} initialError={null} canManage />);

    const search = screen.getByRole('searchbox', { name: 'Buscar pacientes' });
    await user.type(search, '  JO\u00c3O   LOPES ');
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(screen.getByText('Maria Silva')).toBeInTheDocument();
    expect(screen.getByText(/Jo\u00e3o/)).toBeInTheDocument();
    expect(screen.getByText('Pesquisando...')).toBeInTheDocument();
    resolveSearch?.(jsonResponse(pageWith([joao])));
    await waitFor(() => expect(screen.queryByText('Maria Silva')).not.toBeInTheDocument());
    expect(screen.getByText(/Jo\u00e3o/)).toBeInTheDocument();
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/pacientes/pesquisa?');
  });

  it('should_show_search_empty_state_and_keep_tags_actions_after_clearing', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse(pageWith([], 0, 1)))
      .mockResolvedValueOnce(jsonResponse(pageWith([paciente]))));
    render(<PacientesClient initialPage={pageWith([paciente])} availableTags={tags} initialError={null} canManage />);
    const search = screen.getByRole('searchbox', { name: 'Buscar pacientes' });
    await user.type(search, 'inexistente');
    await waitFor(() => expect(screen.getByText(/Nenhum paciente encontrado para/)).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: 'Limpar pesquisa' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Adicionar tag para Maria Silva' })).toBeInTheDocument());
  });

  it('should_abort_previous_search_and_ignore_its_late_response', async () => {
    const user = userEvent.setup();
    const joao = { ...paciente, id: 31, nome: 'Joao Lima' };
    const ana = { ...paciente, id: 32, nome: 'Ana Costa' };
    const resolvers: Array<(response: Response) => void> = [];
    const fetchMock = vi.fn((_path: string, _init?: RequestInit) => new Promise<Response>((resolve) => {
      resolvers.push(resolve);
    }));
    vi.stubGlobal('fetch', fetchMock);
    render(<PacientesClient initialPage={pageWith([paciente])} availableTags={tags} initialError={null} canManage />);

    const search = screen.getByRole('searchbox', { name: 'Buscar pacientes' });
    await user.type(search, 'joao');
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    await user.clear(search);
    await user.type(search, 'ana');
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));

    const firstSignal = (fetchMock.mock.calls[0][1] as RequestInit).signal;
    expect(firstSignal?.aborted).toBe(true);
    resolvers[0](jsonResponse(pageWith([joao])));
    resolvers[1](jsonResponse(pageWith([ana])));
    await waitFor(() => expect(screen.getByText('Ana Costa')).toBeInTheDocument());
    expect(screen.queryByText('Joao Lima')).not.toBeInTheDocument();
  });
});

function pageWith(
  content: PacienteResumo[],
  totalElements = content.length,
  globalTotal = totalElements,
): PacientePage {
  return {
    content,
    number: 0,
    size: 25,
    totalElements,
    totalPages: totalElements > 0 ? 1 : 0,
    counts: {
      total: globalTotal,
      emAtendimento: globalTotal,
      agendado: 0,
      finalizado: 0,
      outros: 0,
    },
  };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
