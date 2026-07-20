import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const services = vi.hoisted(() => ({
  listAtendimentos: vi.fn(),
  getAtendimento: vi.fn().mockResolvedValue(null),
  getMensagens: vi.fn().mockResolvedValue([]),
  getAtendimentoTags: vi.fn().mockResolvedValue([]),
  getAtendimentoLembretes: vi.fn().mockResolvedValue([]),
  marcarAtendimentoComoLido: vi.fn().mockResolvedValue(undefined),
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
    activeId: number | null;
    search: string;
    searching?: boolean;
    onSelect: (id: number) => void;
    onSearchChange: (value: string) => void;
  }) => (
    <div>
      <span data-testid="selected-atendimento">{props.activeId ?? 'nenhum'}</span>
      <button type="button" onClick={() => props.onSelect(7)}>Selecionar atendimento local</button>
      <input
        aria-label="Buscar atendimentos"
        value={props.search}
        onChange={(event) => props.onSearchChange(event.target.value)}
      />
      {props.searching ? <span>Pesquisando...</span> : null}
    </div>
  ),
}));
vi.mock('./ChatWindow', () => ({
  ChatWindow: () => (
    <div data-testid="mock-chat-scroll">
      <textarea aria-label="Rascunho do chat" />
    </div>
  ),
}));
vi.mock('./ContactDetails', () => ({
  ContactDetails: ({ onClose }: { onClose: () => void }) => (
    <div>
      <span>Controles do painel</span>
      <button type="button" onClick={onClose}>Minimizar detalhes do atendimento</button>
    </div>
  ),
}));

import { AtendimentosClient } from './AtendimentosClient';

describe('AtendimentosClient search', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

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

  it('should_hide_and_reopen_details_without_remounting_the_chat_or_calling_the_list_api', async () => {
    window.localStorage.setItem('clinica-crm-atendimentos-details-open', 'true');
    services.listAtendimentos.mockResolvedValue({ content: [], totalElements: 0 });
    const user = userEvent.setup();
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

    const composer = screen.getByRole('textbox', { name: 'Rascunho do chat' });
    await user.type(composer, 'texto preservado');
    const region = screen.getByTestId('contact-details-region');
    await waitFor(() => expect(region).toHaveClass('w-[336px]'));

    await user.click(screen.getByRole('button', { name: 'Minimizar detalhes do atendimento' }));
    expect(region).toHaveClass('w-0');
    expect(region).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByText('Controles do painel')).toBeInTheDocument();
    expect(composer).toHaveValue('texto preservado');

    const reopen = screen.getByRole('button', { name: 'Abrir detalhes do atendimento' });
    expect(reopen).toHaveFocus();
    expect(reopen).toHaveAttribute('aria-expanded', 'false');
    await user.click(reopen);

    expect(region).toHaveClass('w-[336px]');
    expect(screen.getByText('Controles do painel')).toBeInTheDocument();
    expect(composer).toHaveValue('texto preservado');
    expect(services.listAtendimentos).not.toHaveBeenCalled();
  });

  it('should_preserve_selection_and_scroll_when_toggling_details', async () => {
    services.listAtendimentos.mockResolvedValue({ content: [{ id: 7 }], totalElements: 1 });
    const user = userEvent.setup();
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

    const scroll = screen.getByTestId('mock-chat-scroll');
    scroll.scrollTop = 120;
    await user.click(screen.getByRole('button', { name: 'Selecionar atendimento local' }));
    await waitFor(() => expect(services.listAtendimentos).toHaveBeenCalled());
    expect(screen.getByTestId('selected-atendimento')).toHaveTextContent('7');

    const callsBeforeToggle = services.listAtendimentos.mock.calls.length;
    await user.click(screen.getByRole('button', { name: 'Abrir detalhes do atendimento' }));
    await user.click(screen.getByRole('button', { name: 'Minimizar detalhes do atendimento' }));

    expect(screen.getByTestId('selected-atendimento')).toHaveTextContent('7');
    expect(scroll.scrollTop).toBe(120);
    expect(services.listAtendimentos).toHaveBeenCalledTimes(callsBeforeToggle);
  });
});
