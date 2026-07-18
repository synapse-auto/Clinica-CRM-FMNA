import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

const services = vi.hoisted(() => ({
  listAtendimentos: vi.fn(),
  getMensagensRapidasAtivas: vi.fn().mockResolvedValue([]),
  getTagsOperacionaisAtivas: vi.fn().mockResolvedValue([]),
  getNotificacoes: vi.fn().mockResolvedValue([]),
  getNotificacoesResumo: vi.fn().mockResolvedValue(0),
}));

vi.mock('@/services/atendimentos', () => ({
  ...services,
  isWhatsappTemplateRequiredError: vi.fn().mockReturnValue(false),
}));

vi.mock('./ChatList', () => ({
  ChatList: (props: {
    search: string;
    searching?: boolean;
    onSearchChange: (value: string) => void;
  }) => (
    <div>
      <input
        aria-label="Buscar atendimentos"
        value={props.search}
        onChange={(event) => props.onSearchChange(event.target.value)}
      />
      {props.searching ? <span>Pesquisando...</span> : null}
    </div>
  ),
}));
vi.mock('./ChatWindow', () => ({ ChatWindow: () => null }));
vi.mock('./ContactDetails', () => ({ ContactDetails: () => null }));

import { AtendimentosClient } from './AtendimentosClient';

describe('AtendimentosClient search', () => {
  afterEach(() => {
    services.listAtendimentos.mockReset();
    vi.clearAllTimers();
  });

  it('should_debounce_abort_previous_request_and_ignore_late_response', async () => {
    const resolvers: Array<(value: { content: []; totalElements: number }) => void> = [];
    services.listAtendimentos.mockImplementation(() => new Promise((resolve) => resolvers.push(resolve)));
    render(
      <AtendimentosClient
        initialConversations={[]}
        atendentes={[]}
        user={{
          id: 1,
          nome: 'Usuario Teste',
          email: 'user@example.test',
          perfil: 'GESTOR',
          clinicaId: 1,
          mustChangePassword: false,
          podeGerenciarUsuarios: false,
        }}
      />,
    );

    const search = screen.getByRole('textbox', { name: 'Buscar atendimentos' });
    fireEvent.change(search, { target: { value: 'joao' } });
    await waitFor(() => expect(services.listAtendimentos).toHaveBeenCalledTimes(1));
    fireEvent.change(search, { target: { value: 'ana' } });
    await waitFor(() => expect(services.listAtendimentos).toHaveBeenCalledTimes(2));

    const firstSignal = services.listAtendimentos.mock.calls[0][1] as AbortSignal;
    expect(firstSignal.aborted).toBe(true);
    expect(screen.getByText('Pesquisando...')).toBeInTheDocument();
    resolvers[0]({ content: [], totalElements: 0 });
    resolvers[1]({ content: [], totalElements: 0 });
    await waitFor(() => expect(screen.queryByText('Pesquisando...')).not.toBeInTheDocument());
  });
});
