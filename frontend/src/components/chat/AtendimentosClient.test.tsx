import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useEffect, useState } from 'react';

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
  iniciarAtendimento: vi.fn(),
  encerrarAtendimento: vi.fn(),
  contarAtendimentosAtivos: vi.fn(),
  encerrarTodosAtendimentos: vi.fn(),
}));

vi.mock('@/services/atendimentos', () => ({
  ...services,
  isWhatsappTemplateRequiredError: vi.fn().mockReturnValue(false),
}));

vi.mock('./ChatList', () => ({
  ChatList: (props: {
    activeId: number | null;
    conversations: { id: number }[];
    view: 'ATIVOS' | 'FINALIZADOS';
    filter: string;
    type: string;
    search: string;
    searching?: boolean;
    onSelect: (id: number) => void;
    onViewChange: (view: 'ATIVOS' | 'FINALIZADOS') => void;
    onFilterChange: (filter: 'TODOS' | 'MEUS', type: 'TODOS') => void;
    onSearchChange: (value: string) => void;
    onStartManual?: () => void;
    onCloseAll?: () => void;
    canCloseAll?: boolean;
  }) => (
    <div>
      <span data-testid="selected-atendimento">{props.activeId ?? 'nenhum'}</span>
      <span data-testid="current-view">{props.view}</span>
      <span data-testid="current-filter">{props.filter}/{props.type}</span>
      <span data-testid="conversation-ids">{props.conversations.map((item) => item.id).join(',')}</span>
      <button type="button" onClick={() => props.onSelect(7)}>Selecionar atendimento local</button>
      <button type="button" onClick={() => props.onSelect(8)}>Selecionar B</button>
      <button type="button" onClick={() => props.onSelect(9)}>Selecionar C</button>
      <button type="button" onClick={() => props.onViewChange('ATIVOS')}>Em atendimento</button>
      <button type="button" onClick={() => props.onViewChange('FINALIZADOS')}>Finalizados</button>
      <button type="button" onClick={() => props.onFilterChange('MEUS', 'TODOS')}>Filtro Meus</button>
      <input
        aria-label="Buscar atendimentos"
        value={props.search}
        onChange={(event) => props.onSearchChange(event.target.value)}
      />
      {props.onStartManual ? (
        <button type="button" onClick={props.onStartManual}>Novo atendimento</button>
      ) : null}
      {props.canCloseAll ? (
        <button type="button" onClick={props.onCloseAll}>Encerrar todos</button>
      ) : null}
      {props.searching ? <span>Pesquisando...</span> : null}
    </div>
  ),
}));
vi.mock('./ChatWindow', () => ({
  ChatWindow: (props: {
    detail: { id?: number } | null;
    loading?: boolean;
    initialDraft?: string;
    onDraftChange?: (content: string) => void;
    onSend?: (content: string) => Promise<void>;
  }) => {
    const [draft, setDraft] = useState(props.initialDraft ?? '');
    useEffect(() => setDraft(props.initialDraft ?? ''), [props.initialDraft]);
    return (
      <div data-testid="mock-chat-scroll">
      <span data-testid="chat-detail-id">{props.detail?.id ?? 'none'}</span>
      <span data-testid="chat-loading">{props.loading ? 'loading' : 'idle'}</span>
      <textarea
        aria-label="Rascunho do chat"
        value={draft}
        onChange={(event) => {
          setDraft(event.target.value);
          props.onDraftChange?.(event.target.value);
        }}
      />
      <button
        type="button"
        onClick={() => {
          const sending = props.onSend?.(draft);
          if (sending) {
            void sending
              .then(() => {
                setDraft('');
                props.onDraftChange?.('');
              })
              .catch(() => undefined);
          }
        }}
      >
        Reenviar rascunho
      </button>
    </div>
    );
  },
}));
vi.mock('./ContactDetails', () => ({
  ContactDetails: ({
    onClose,
    onEncerrarAtendimento,
  }: {
    onClose: () => void;
    onEncerrarAtendimento?: () => void;
  }) => (
    <div>
      <span>Controles do painel</span>
      <button type="button" onClick={onClose}>Minimizar detalhes do atendimento</button>
      {onEncerrarAtendimento ? (
        <button type="button" onClick={onEncerrarAtendimento}>Encerrar atendimento</button>
      ) : null}
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

  it('should_open_the_query_param_attendance_even_when_it_is_not_in_the_initial_page', async () => {
    services.getAtendimento.mockResolvedValueOnce({ id: 99 });
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
        initialAtendimentoId={99}
      />,
    );

    await waitFor(() => expect(services.getAtendimento).toHaveBeenCalledWith(
      99,
      expect.any(AbortSignal),
    ));
    expect(screen.getByTestId('selected-atendimento')).toHaveTextContent('99');
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

describe('AtendimentosClient visões de atendimento', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.history.replaceState({}, '', '/atendimentos');
    services.getMensagens.mockResolvedValue([]);
    services.getAtendimentoTags.mockResolvedValue([]);
    services.getAtendimentoLembretes.mockResolvedValue([]);
    services.marcarAtendimentoComoLido.mockResolvedValue(undefined);
    services.getAtendimento.mockImplementation((id: number) => Promise.resolve({
      id,
      status: id === 8 ? 'ENCERRADO' : 'ATIVO',
    }));
  });

  afterEach(() => {
    services.listAtendimentos.mockReset();
    services.getAtendimento.mockReset();
    services.getMensagens.mockReset();
    services.getAtendimentoTags.mockReset();
    services.getAtendimentoLembretes.mockReset();
    services.marcarAtendimentoComoLido.mockReset();
  });

  it('should_load_finalized_history_with_its_own_filter_and_replace_the_active_selection', async () => {
    let resolveFinalizedList: ((value: { content: Array<{ id: number; status: string }>; totalElements: number }) => void) | null = null;
    services.listAtendimentos.mockImplementation(({ filtro }: { filtro: string }) => (
      filtro === 'FINALIZADOS'
        ? new Promise((resolve) => { resolveFinalizedList = resolve; })
        : Promise.resolve({ content: [{ id: 7, status: 'ATIVO' }], totalElements: 1 })
    ));
    const user = userEvent.setup();
    render(<AtendimentosClient initialConversations={[{ id: 7, status: 'ATIVO' }]} atendentes={[]} user={gestor} />);

    await user.click(screen.getByRole('button', { name: 'Finalizados' }));
    expect(screen.getByTestId('selected-atendimento')).toHaveTextContent('nenhum');
    expect(screen.getByTestId('chat-detail-id')).toHaveTextContent('none');
    expect(screen.getByTestId('chat-loading')).toHaveTextContent('loading');

    resolveFinalizedList?.({ content: [{ id: 8, status: 'ENCERRADO' }], totalElements: 1 });

    await waitFor(() => expect(screen.getByTestId('selected-atendimento')).toHaveTextContent('8'));
    expect(screen.getByTestId('current-view')).toHaveTextContent('FINALIZADOS');
    expect(screen.getByTestId('conversation-ids')).toHaveTextContent('8');
    await waitFor(() => expect(screen.getByTestId('chat-detail-id')).toHaveTextContent('8'));
    expect(services.listAtendimentos).toHaveBeenCalledWith(
      { filtro: 'FINALIZADOS', tipo: 'TODOS', busca: '' }, expect.any(AbortSignal),
    );
    expect(window.location.search).toBe('?visao=finalizados&atendimentoId=8');
  });

  it('should_keep_operational_filter_and_search_when_returning_from_history', async () => {
    services.listAtendimentos.mockResolvedValue({ content: [{ id: 7, status: 'ATIVO' }], totalElements: 1 });
    const user = userEvent.setup();
    render(<AtendimentosClient initialConversations={[{ id: 7, status: 'ATIVO' }]} atendentes={[]} user={gestor} />);

    await user.click(screen.getByRole('button', { name: 'Filtro Meus' }));
    await user.type(screen.getByRole('textbox', { name: 'Buscar atendimentos' }), 'Maria');
    await user.click(screen.getByRole('button', { name: 'Finalizados' }));
    await user.click(screen.getByRole('button', { name: 'Em atendimento' }));

    expect(screen.getByTestId('current-view')).toHaveTextContent('ATIVOS');
    expect(screen.getByTestId('current-filter')).toHaveTextContent('MEUS/TODOS');
    expect(screen.getByRole('textbox', { name: 'Buscar atendimentos' })).toHaveValue('Maria');
  });

  it('should_keep_the_history_url_without_selection_when_the_finalized_result_is_empty', async () => {
    services.listAtendimentos.mockResolvedValue({ content: [], totalElements: 0 });
    const user = userEvent.setup();
    render(<AtendimentosClient initialConversations={[{ id: 7, status: 'ATIVO' }]} atendentes={[]} user={gestor} />);

    await user.click(screen.getByRole('button', { name: 'Finalizados' }));

    await waitFor(() => expect(services.listAtendimentos).toHaveBeenCalledWith(
      { filtro: 'FINALIZADOS', tipo: 'TODOS', busca: '' }, expect.any(AbortSignal),
    ));
    expect(screen.getByTestId('selected-atendimento')).toHaveTextContent('nenhum');
    expect(screen.getByTestId('chat-detail-id')).toHaveTextContent('none');
    expect(window.location.search).toBe('?visao=finalizados');
  });

  it('should_restore_the_finalized_view_from_the_url_without_opening_the_active_initial_item', async () => {
    services.listAtendimentos.mockImplementation(({ filtro }: { filtro: string }) => Promise.resolve({
      content: filtro === 'FINALIZADOS' ? [{ id: 8, status: 'ENCERRADO' }] : [{ id: 7, status: 'ATIVO' }],
      totalElements: 1,
    }));
    render(
      <AtendimentosClient
        initialConversations={[{ id: 7, status: 'ATIVO' }]}
        atendentes={[]}
        user={gestor}
        initialAtendimentoId={8}
        initialView="FINALIZADOS"
      />,
    );

    expect(screen.getByTestId('current-view')).toHaveTextContent('FINALIZADOS');
    expect(screen.getByTestId('selected-atendimento')).toHaveTextContent('nenhum');
    await waitFor(() => expect(screen.getByTestId('selected-atendimento')).toHaveTextContent('8'));
    expect(services.getAtendimento).not.toHaveBeenCalledWith(7, expect.any(AbortSignal));
    expect(window.location.search).toBe('?visao=finalizados&atendimentoId=8');
  });
});

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
    services.iniciarAtendimento.mockReset();
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

  it('should_keep_failed_initial_message_as_draft_and_retry_without_starting_again', async () => {
    services.listAtendimentos.mockResolvedValue({
      content: [{ id: 7 }, { id: 8 }, { id: 9 }, { id: 44 }],
      totalElements: 4,
    });
    services.iniciarAtendimento.mockResolvedValue({
      atendimentoId: 44,
      pacienteId: 20,
      modo: 'HUMANO',
      atendimento: {
        id: 44,
        whatsappCapabilities: {
          provider: 'UAZAP',
          enforcesCustomerCareWindow: false,
          supportsMessageTemplates: false,
        },
      },
    });
    services.enviarMensagem
      .mockResolvedValueOnce({
        id: 70,
        whatsappStatus: 'FALHA',
        motivoFalha: 'Número indisponível',
      })
      .mockResolvedValueOnce({
        id: 71,
        whatsappStatus: 'ENVIADA',
        motivoFalha: null,
      });
    const user = userEvent.setup();
    render(<AtendimentosClient initialConversations={[]} atendentes={[]} user={gestor} />);

    await user.click(screen.getByRole('button', { name: 'Novo atendimento' }));
    await user.type(screen.getByRole('textbox', { name: 'Nome do contato' }), 'Maria Teste');
    await user.type(screen.getByRole('textbox', { name: 'Telefone' }), '83999999999');
    await user.type(
      screen.getByRole('textbox', { name: /Primeira mensagem/ }),
      'Mensagem inicial preservada',
    );
    await user.click(screen.getByRole('button', { name: 'Iniciar atendimento' }));

    await waitFor(() => expect(services.enviarMensagem).toHaveBeenCalledWith(
      44,
      'Mensagem inicial preservada',
    ));
    expect(screen.getByRole('textbox', { name: 'Rascunho do chat' }))
      .toHaveValue('Mensagem inicial preservada');

    await user.click(screen.getByRole('button', { name: 'Reenviar rascunho' }));
    await waitFor(() => expect(services.enviarMensagem).toHaveBeenCalledTimes(2));
    expect(services.iniciarAtendimento).toHaveBeenCalledTimes(1);
  });

  it('should_preserve_list_context_and_reconcile_new_manual_attendance_immediately', async () => {
    services.iniciarAtendimento.mockResolvedValue({
      atendimentoId: 44,
      pacienteId: 20,
      modo: 'HUMANO',
      atendimento: { id: 44, whatsappCapabilities: {} },
    });
    services.listAtendimentos.mockResolvedValue({
      content: [{ id: 7 }, { id: 8 }, { id: 44 }],
      totalElements: 3,
    });
    services.getAtendimento.mockResolvedValue({ id: 44 });
    const user = userEvent.setup();
    render(<AtendimentosClient initialConversations={[{ id: 7 }, { id: 8 }]} atendentes={[]} user={gestor} />);

    await user.click(screen.getByRole('button', { name: 'Novo atendimento' }));
    await user.type(screen.getByRole('textbox', { name: 'Nome do contato' }), 'Maria Teste');
    await user.type(screen.getByRole('textbox', { name: 'Telefone' }), '83999999999');
    await user.click(screen.getByRole('button', { name: 'Iniciar atendimento' }));

    await waitFor(() => expect(screen.getByTestId('selected-atendimento')).toHaveTextContent('44'));
    await waitFor(() => expect(screen.getByTestId('chat-detail-id')).toHaveTextContent('44'));
    expect(screen.getByTestId('current-filter')).toHaveTextContent('TODOS/TODOS');
    expect(screen.getByTestId('conversation-ids')).toHaveTextContent('7,8,44');
    expect(services.listAtendimentos).toHaveBeenCalledWith(
      { filtro: 'TODOS', tipo: 'TODOS', busca: '' }, expect.any(AbortSignal),
    );
    expect(window.location.search).toBe('?atendimentoId=44');
  });

  it('should_refresh_the_open_chat_and_show_transfer_notice_when_a_new_notification_arrives', async () => {
    vi.useFakeTimers();
    services.getNotificacoes
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([{
        id: 91,
        atendimentoId: 7,
        tipo: 'TRANSFERENCIA_IA',
        descricao: 'A IA transferiu um atendimento para humano',
        lida: false,
        criadoEm: '2026-07-27T12:00:00Z',
      }]);
    services.getNotificacoesResumo.mockResolvedValue(1);
    services.listAtendimentos.mockResolvedValue({ content: [{ id: 7 }], totalElements: 1 });
    services.getAtendimento.mockResolvedValue({ id: 7 });
    services.getMensagens.mockResolvedValue([]);

    render(<AtendimentosClient initialConversations={[{ id: 7 }]} atendentes={[]} user={gestor} />);
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    const detailCallsBeforeNotification = services.getAtendimento.mock.calls.length;

    act(() => {
      vi.advanceTimersByTime(5000);
    });
    await act(async () => { await Promise.resolve(); });

    expect(screen.getByRole('status')).toHaveTextContent('A IA transferiu um atendimento para humano');
    expect(services.getAtendimento.mock.calls.length).toBeGreaterThan(detailCallsBeforeNotification);
    vi.useRealTimers();
  });
});

describe('AtendimentosClient encerramento', () => {
  beforeEach(() => {
    window.localStorage.clear();
    services.listAtendimentos.mockResolvedValue({ content: [{ id: 8 }], totalElements: 1 });
    services.getAtendimento.mockImplementation((id: number) => Promise.resolve({ id, status: 'ATIVO' }));
    services.getMensagens.mockResolvedValue([]);
    services.getAtendimentoTags.mockResolvedValue([]);
    services.getAtendimentoLembretes.mockResolvedValue([]);
    services.marcarAtendimentoComoLido.mockResolvedValue(undefined);
    services.encerrarAtendimento.mockReset();
    services.contarAtendimentosAtivos.mockReset();
    services.encerrarTodosAtendimentos.mockReset();
  });

  it('should_remove_closed_attendance_select_the_next_one_and_update_the_url', async () => {
    services.encerrarAtendimento.mockResolvedValue({ id: 7, status: 'ENCERRADO' });
    let atendimentoFoiEncerrado = false;
    services.encerrarAtendimento.mockImplementation(async () => {
      atendimentoFoiEncerrado = true;
      return { id: 7, status: 'ENCERRADO' };
    });
    services.listAtendimentos.mockImplementation(() => Promise.resolve({
      content: atendimentoFoiEncerrado ? [{ id: 8 }] : [{ id: 7 }, { id: 8 }],
      totalElements: atendimentoFoiEncerrado ? 1 : 2,
    }));
    window.localStorage.setItem('clinica-crm-atendimentos-details-open', 'true');
    const user = userEvent.setup();
    render(<AtendimentosClient initialConversations={[{ id: 7 }, { id: 8 }]} atendentes={[]} user={gestor} />);

    const closeAction = screen.getByRole('button', { name: 'Encerrar atendimento' });
    await user.click(closeAction);
    expect(screen.getByRole('dialog', { name: 'Encerrar atendimento?' })).toBeInTheDocument();

    await user.click(screen.getAllByRole('button', { name: 'Encerrar atendimento' }).at(-1)!);

    await waitFor(() => expect(services.encerrarAtendimento).toHaveBeenCalledWith(7));
    await waitFor(() => expect(screen.getByTestId('selected-atendimento')).toHaveTextContent('8'));
    expect(screen.getByTestId('conversation-ids')).toHaveTextContent('8');
    expect(window.location.search).toBe('?atendimentoId=8');
    expect(screen.getByRole('status')).toHaveTextContent('Atendimento encerrado.');
  });

  it('should_not_show_close_all_for_medico', () => {
    render(
      <AtendimentosClient
        initialConversations={[]}
        atendentes={[]}
        user={{ ...gestor, perfil: 'MEDICO' }}
      />,
    );

    expect(screen.queryByRole('button', { name: 'Encerrar todos' })).not.toBeInTheDocument();
  });

  it('should_not_call_bulk_closure_when_active_count_is_zero', async () => {
    services.contarAtendimentosAtivos.mockResolvedValue({ total: 0 });
    const user = userEvent.setup();
    render(<AtendimentosClient initialConversations={[]} atendentes={[]} user={gestor} />);

    await user.click(screen.getByRole('button', { name: 'Encerrar todos' }));

    await waitFor(() => expect(services.contarAtendimentosAtivos).toHaveBeenCalledOnce());
    expect(services.encerrarTodosAtendimentos).not.toHaveBeenCalled();
    expect(screen.getByRole('status')).toHaveTextContent('Não há atendimentos ativos para encerrar.');
  });

  it('should_require_strong_confirmation_and_clear_selection_after_bulk_closure', async () => {
    services.contarAtendimentosAtivos.mockResolvedValue({ total: 2 });
    services.encerrarTodosAtendimentos.mockResolvedValue({ encerrados: 2, dataEncerramento: '2026-07-29T12:00:00Z' });
    services.listAtendimentos.mockResolvedValue({ content: [], totalElements: 0 });
    const user = userEvent.setup();
    render(<AtendimentosClient initialConversations={[{ id: 7 }, { id: 8 }]} atendentes={[]} user={gestor} />);

    await user.click(screen.getByRole('button', { name: 'Encerrar todos' }));
    await waitFor(() => expect(screen.getByRole('dialog', { name: 'Encerrar todos os atendimentos?' })).toBeInTheDocument());
    const confirm = screen.getAllByRole('button', { name: 'Encerrar 2 atendimentos' }).at(-1)!;
    expect(confirm).toBeDisabled();

    await user.type(screen.getByRole('textbox', { name: 'Confirmação para encerrar todos' }), 'ENCERRAR TODOS');
    expect(confirm).toBeEnabled();
    await user.click(confirm);

    await waitFor(() => expect(services.encerrarTodosAtendimentos).toHaveBeenCalledWith({ confirmado: true }));
    await waitFor(() => expect(screen.getByTestId('selected-atendimento')).toHaveTextContent('nenhum'));
    expect(screen.getByTestId('conversation-ids')).toHaveTextContent('');
    expect(window.location.search).toBe('');
    expect(screen.getByRole('status')).toHaveTextContent('2 atendimentos encerrados.');
  });
});
