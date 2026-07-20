import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const services = vi.hoisted(() => ({
  listAtendimentos: vi.fn(),
  getAtendimento: vi.fn().mockResolvedValue(null),
  getMensagens: vi.fn().mockResolvedValue([]),
  getAtendimentoTags: vi.fn().mockResolvedValue([]),
  getAtendimentoLembretes: vi.fn().mockResolvedValue([]),
  marcarAtendimentoComoLido: vi.fn().mockResolvedValue(undefined),
  enviarMensagem: vi.fn().mockResolvedValue(null),
  enviarAnexo: vi.fn().mockResolvedValue(null),
  enviarWhatsappTemplate: vi.fn().mockResolvedValue(null),
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
      <button type="button" onClick={() => props.onSelect(8)}>Selecionar B</button>
      <button type="button" onClick={() => props.onSelect(9)}>Selecionar C</button>
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
  ChatWindow: (props: { detail: { id?: number } | null; loading?: boolean }) => (
    <div data-testid="mock-chat-scroll">
      <span data-testid="chat-detail-id">{props.detail?.id ?? 'none'}</span>
      <span data-testid="chat-loading">{props.loading ? 'loading' : 'idle'}</span>
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

const gestor = {
  id: 1,
  nome: 'Usuario Teste',
  email: 'user@example.test',
  perfil: 'GESTOR' as const,
  clinicaId: 1,
  mustChangePassword: false,
  podeGerenciarUsuarios: false,
};

describe('AtendimentosClient troca de conversa (latência)', () => {
  beforeEach(() => {
    window.localStorage.clear();
    // A lista contém as conversas selecionáveis para que o refresh em segundo plano
    // (após marcar como lido) não desmarque a seleção corrente.
    services.listAtendimentos.mockResolvedValue({
      content: [{ id: 7 }, { id: 8 }, { id: 9 }],
      totalElements: 3,
    });
    services.getAtendimento.mockImplementation((id: number) => Promise.resolve({ id }));
    services.getMensagens.mockResolvedValue([]);
    services.getAtendimentoTags.mockResolvedValue([]);
    services.getAtendimentoLembretes.mockResolvedValue([]);
    services.marcarAtendimentoComoLido.mockResolvedValue(undefined);
    services.enviarMensagem.mockClear();
    services.enviarWhatsappTemplate.mockClear();
  });

  afterEach(() => {
    services.getAtendimento.mockReset();
    services.getMensagens.mockReset();
    services.getAtendimentoTags.mockReset();
    services.getAtendimentoLembretes.mockReset();
    services.marcarAtendimentoComoLido.mockReset();
    services.listAtendimentos.mockReset();
    vi.clearAllTimers();
  });

  it('should_change_selection_immediately_and_load_content_without_waiting_for_mark_as_read', async () => {
    let resolveMark: () => void = () => {};
    services.marcarAtendimentoComoLido.mockImplementation(
      () => new Promise<void>((resolve) => { resolveMark = resolve; }),
    );
    const user = userEvent.setup();
    render(<AtendimentosClient initialConversations={[]} atendentes={[]} user={gestor} />);

    await user.click(screen.getByRole('button', { name: 'Selecionar atendimento local' }));
    // Seleção muda na hora, antes de qualquer resolução de API.
    expect(screen.getByTestId('selected-atendimento')).toHaveTextContent('7');
    // Conteúdo crítico carrega mesmo com marcar-como-lido ainda pendente.
    await waitFor(() => expect(screen.getByTestId('chat-detail-id')).toHaveTextContent('7'));
    expect(services.getAtendimento).toHaveBeenCalledWith(7, expect.any(AbortSignal));
    expect(services.getMensagens).toHaveBeenCalledWith(7, expect.any(AbortSignal));
    act(() => resolveMark());
  });

  it('should_keep_the_conversation_open_when_mark_as_read_fails', async () => {
    services.marcarAtendimentoComoLido.mockRejectedValue(new Error('falha ao marcar'));
    const user = userEvent.setup();
    render(<AtendimentosClient initialConversations={[]} atendentes={[]} user={gestor} />);

    await user.click(screen.getByRole('button', { name: 'Selecionar atendimento local' }));
    await waitFor(() => expect(screen.getByTestId('chat-detail-id')).toHaveTextContent('7'));
  });

  it('should_render_critical_content_without_waiting_for_tags_or_reminders', async () => {
    services.getAtendimentoTags.mockImplementation(() => new Promise(() => {})); // nunca resolve
    services.getAtendimentoLembretes.mockImplementation(() => new Promise(() => {})); // nunca resolve
    const user = userEvent.setup();
    render(<AtendimentosClient initialConversations={[]} atendentes={[]} user={gestor} />);

    await user.click(screen.getByRole('button', { name: 'Selecionar atendimento local' }));
    // Detalhe + mensagens aparecem e o carregamento crítico termina apesar de tags/lembretes pendentes.
    await waitFor(() => expect(screen.getByTestId('chat-detail-id')).toHaveTextContent('7'));
    await waitFor(() => expect(screen.getByTestId('chat-loading')).toHaveTextContent('idle'));
  });

  it('should_show_only_the_last_conversation_when_switching_quickly_A_B_C', async () => {
    const pending = new Map<number, { resolve: () => void; signal?: AbortSignal }>();
    services.getAtendimento.mockImplementation((id: number, signal?: AbortSignal) => (
      new Promise((resolve) => { pending.set(id, { resolve: () => resolve({ id }), signal }); })
    ));
    const user = userEvent.setup();
    render(<AtendimentosClient initialConversations={[]} atendentes={[]} user={gestor} />);

    await user.click(screen.getByRole('button', { name: 'Selecionar atendimento local' })); // A = 7
    await user.click(screen.getByRole('button', { name: 'Selecionar B' })); // B = 8
    await user.click(screen.getByRole('button', { name: 'Selecionar C' })); // C = 9
    expect(screen.getByTestId('selected-atendimento')).toHaveTextContent('9');

    // Respostas chegam fora de ordem: B, A, C.
    await act(async () => { pending.get(8)?.resolve(); });
    await act(async () => { pending.get(7)?.resolve(); });
    await act(async () => { pending.get(9)?.resolve(); });

    // A tela final mostra somente C (9); respostas antigas foram descartadas.
    await waitFor(() => expect(screen.getByTestId('chat-detail-id')).toHaveTextContent('9'));
    // As requisições anteriores foram canceladas via AbortController.
    expect(pending.get(7)?.signal?.aborted).toBe(true);
    expect(pending.get(8)?.signal?.aborted).toBe(true);
    expect(pending.get(9)?.signal?.aborted).toBe(false);
  });

  it('should_issue_only_internal_calls_and_no_whatsapp_sending_when_selecting', async () => {
    const user = userEvent.setup();
    render(<AtendimentosClient initialConversations={[]} atendentes={[]} user={gestor} />);

    await user.click(screen.getByRole('button', { name: 'Selecionar atendimento local' }));
    await waitFor(() => expect(screen.getByTestId('chat-detail-id')).toHaveTextContent('7'));

    // Somente APIs internas do CRM participam da troca.
    expect(services.getAtendimento).toHaveBeenCalledTimes(1);
    expect(services.getMensagens).toHaveBeenCalledTimes(1);
    expect(services.getAtendimentoTags).toHaveBeenCalledTimes(1);
    expect(services.getAtendimentoLembretes).toHaveBeenCalledTimes(1);
    expect(services.marcarAtendimentoComoLido).toHaveBeenCalledTimes(1);
    // Nenhum envio ao WhatsApp/Meta ao apenas selecionar uma conversa.
    expect(services.enviarMensagem).not.toHaveBeenCalled();
    expect(services.enviarWhatsappTemplate).not.toHaveBeenCalled();
  });
});
