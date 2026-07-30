import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getWhatsappTemplates } from '@/services/atendimentos';
import { ChatWindow } from './ChatWindow';
import type { AtendimentoDetalhe, MensagemAtendimento, WhatsappTemplate } from '@/types/atendimento';

vi.mock('@/services/atendimentos', () => ({
  getWhatsappTemplates: vi.fn(),
}));

const getTemplatesMock = vi.mocked(getWhatsappTemplates);

const detail: AtendimentoDetalhe = {
  id: 30,
  status: 'ATIVO',
  tratadoPorIa: false,
  dataInicio: '2026-07-01T12:00:00Z',
  dataEncerramento: null,
  naoLidas: 0,
  paciente: {
    id: 10,
    nome: 'Paciente Teste',
    telefone: '44 99999-9999',
    email: null,
    status: 'EM_ATENDIMENTO',
    fotoUrl: null,
    ultimaInteracaoEm: null,
    requerRevisao: false,
    convenioStatus: null,
    convenioRevisadoEm: null,
    convenioRevisadoPorId: null,
    convenioRevisadoPorNome: null,
  },
  atendentePrincipal: null,
  janelaWhatsappAberta: true,
  janelaWhatsappExpiraEm: '2026-07-16T18:00:00Z',
  ultimaMensagemEntradaEm: '2026-07-15T18:00:00Z',
  aguardandoRespostaTemplate: false,
  whatsappTemplatesDisponiveis: true,
};

const uazapDetail: AtendimentoDetalhe = {
  ...detail,
  janelaWhatsappAberta: false,
  janelaWhatsappExpiraEm: '2026-07-14T18:00:00Z',
  whatsappTemplatesDisponiveis: false,
  whatsappCapabilities: {
    provider: 'UAZAP',
    enforcesCustomerCareWindow: false,
    supportsMessageTemplates: false,
  },
};

const scrollIntoViewMock = vi.fn();
const scrollToMock = vi.fn(function scrollTo(this: HTMLElement, options: ScrollToOptions) {
  this.scrollTop = Number(options.top ?? 0);
});

const template: WhatsappTemplate = {
  id: 'template-1',
  nome: 'retomar_atendimento',
  idioma: 'pt_BR',
  status: 'APPROVED',
  categoria: 'UTILITY',
  cabecalho: null,
  corpo: 'Podemos continuar seu atendimento?',
  rodape: null,
  botoes: [],
  variaveis: [],
  suportado: true,
  motivoNaoSuportado: null,
};

beforeEach(() => {
  getTemplatesMock.mockReset();
  getTemplatesMock.mockResolvedValue([template]);
  scrollIntoViewMock.mockClear();
  Object.defineProperty(Element.prototype, 'scrollIntoView', {
    configurable: true,
    value: scrollIntoViewMock,
  });
  Object.defineProperty(HTMLElement.prototype, 'scrollHeight', {
    configurable: true,
    value: 1000,
  });
  Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
    configurable: true,
    value: 360,
  });
  Object.defineProperty(HTMLElement.prototype, 'scrollTop', {
    configurable: true,
    writable: true,
    value: 0,
  });
  scrollToMock.mockClear();
  Object.defineProperty(HTMLElement.prototype, 'scrollTo', {
    configurable: true,
    value: scrollToMock,
  });
  vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback: FrameRequestCallback) => {
    callback(0);
    return 1;
  });
});

describe('ChatWindow estabilidade visual do envio', () => {
  it('should_keep_a_fixed_status_area_when_pending_messages_change', () => {
    const { rerender } = render(
      <ChatWindow
        detail={detail}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        pendingTextMessageCount={0}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    const status = screen.getByTestId('pending-text-status');
    expect(status).toHaveClass('min-h-4', 'mt-2');
    expect(status).toBeEmptyDOMElement();

    rerender(
      <ChatWindow
        detail={detail}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        pendingTextMessageCount={1}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(screen.getByTestId('pending-text-status')).toHaveClass('min-h-4', 'mt-2');
    expect(screen.getByTestId('pending-text-status')).toHaveTextContent('Enviando 1 mensagem');
  });

  it('should_not_scroll_when_an_optimistic_message_is_acknowledged_in_place', () => {
    const pending = { ...makeMessage(-1, 'SAIDA'), conteudo: 'Mensagem pendente', conteudoPrevia: 'Mensagem pendente', whatsappStatus: 'PENDENTE' };
    const confirmed = { ...pending, id: 101, whatsappStatus: 'ENVIADA' };
    const { rerender } = render(
      <ChatWindow
        detail={detail}
        messages={[pending]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    scrollToMock.mockClear();
    rerender(
      <ChatWindow
        detail={detail}
        messages={[confirmed]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(scrollToMock).not.toHaveBeenCalled();
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

function makeMessage(id: number, direcao: MensagemAtendimento['direcao'] = 'ENTRADA'): MensagemAtendimento {
  return {
    id,
    direcao,
    remetente: direcao === 'SAIDA' ? 'ATENDENTE' : 'PACIENTE',
    tipoMedia: 'TEXTO',
    conteudo: `Mensagem ${id}`,
    conteudoPrevia: `Mensagem ${id}`,
    whatsappStatus: direcao === 'SAIDA' ? 'ENVIADA' : 'RECEBIDA',
    motivoFalha: null,
    dataHora: new Date(2026, 6, 1, 10, id).toISOString(),
    entregueEm: null,
    lidaEm: null,
    midia: null,
    templateNome: null,
    templateIdioma: null,
  };
}

function setScrollMetrics(element: HTMLElement, metrics: { scrollHeight: number; clientHeight: number; scrollTop: number }) {
  Object.defineProperty(element, 'scrollHeight', { configurable: true, value: metrics.scrollHeight });
  Object.defineProperty(element, 'clientHeight', { configurable: true, value: metrics.clientHeight });
  Object.defineProperty(element, 'scrollTop', { configurable: true, writable: true, value: metrics.scrollTop });
}

describe('ChatWindow', () => {
  it('should_group_messages_by_local_calendar_day_with_semantic_separators', () => {
    const currentDate = new Date();
    const todayDate = new Date(currentDate.getFullYear(), currentDate.getMonth(), currentDate.getDate(), 9);
    const yesterdayDate = new Date(currentDate.getFullYear(), currentDate.getMonth(), currentDate.getDate() - 1, 18);
    const today = todayDate.toISOString();
    const yesterday = yesterdayDate.toISOString();
    render(
      <ChatWindow
        detail={detail}
        messages={[
          { ...makeMessage(1), dataHora: yesterday },
          { ...makeMessage(2, 'SAIDA'), dataHora: today },
          { ...makeMessage(3), dataHora: today, tipoMedia: 'AI_HANDOFF_SUMMARY' },
        ]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(screen.getAllByText('Hoje')).toHaveLength(1);
    expect(screen.getAllByText('Ontem')).toHaveLength(1);
    expect(screen.getAllByTestId(/^chat-date-/)[0].querySelector('time')).toHaveAttribute('dateTime', expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/));
  });

  it('should_ignore_invalid_message_dates_without_breaking_the_history', () => {
    render(
      <ChatWindow
        detail={detail}
        messages={[{ ...makeMessage(1), dataHora: 'invalid' }]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(screen.getByText('Mensagem 1')).toBeInTheDocument();
    expect(screen.getByText('Horário indisponível')).toBeInTheDocument();
    expect(screen.queryByText('Invalid Date')).not.toBeInTheDocument();
  });

  it('should_render_closed_attendance_as_read_only_without_sending_controls', () => {
    const onSend = vi.fn();
    const onAttach = vi.fn();
    render(
      <ChatWindow
        detail={{ ...detail, status: 'ENCERRADO', dataEncerramento: '2026-07-29T12:00:00Z' }}
        messages={[makeMessage(1)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={onSend}
        onAttach={onAttach}
      />,
    );

    expect(screen.getByText(/histórico permanece disponível somente para leitura/i)).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('Digite uma mensagem...')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Enviar' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Adicionar' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Janela do WhatsApp aberta/)).not.toBeInTheDocument();
    expect(onSend).not.toHaveBeenCalled();
    expect(onAttach).not.toHaveBeenCalled();
  });

  const quickMessages = [
    {
      id: 90,
      categoriaId: null,
      categoriaRotulo: null,
      categoriaCor: null,
      titulo: 'Saudação',
      atalho: '/saudacao',
      conteudo: 'Olá! Como posso ajudar?\nConte comigo. 😊',
      ativo: true,
      criadoEm: null,
      atualizadoEm: null,
    },
  ];

  it('should_expand_an_exact_active_quick_message_on_first_enter_without_sending', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn();
    render(<ChatWindow detail={detail} messages={[]} quickMessages={quickMessages} busy={false} error={null} onSend={onSend} onAttach={async () => undefined} />);

    const composer = screen.getByPlaceholderText('Digite uma mensagem...');
    await user.type(composer, '  /SAUDACAO  ');
    await user.keyboard('{Enter}');

    expect(composer).toHaveValue('Olá! Como posso ajudar?\nConte comigo. 😊');
    expect(onSend).not.toHaveBeenCalled();

    await user.keyboard('{Enter}');
    await waitFor(() => expect(onSend).toHaveBeenCalledWith('Olá! Como posso ajudar?\nConte comigo. 😊'));
  });

  it('should_not_expand_partial_or_inactive_shortcuts_and_keeps_normal_send', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn();
    render(<ChatWindow detail={detail} messages={[]} quickMessages={[...quickMessages, { ...quickMessages[0], id: 91, atalho: '/inativa', ativo: false }]} busy={false} error={null} onSend={onSend} onAttach={async () => undefined} />);

    const composer = screen.getByPlaceholderText('Digite uma mensagem...');
    await user.type(composer, 'preciso de /saudacao agora');
    await user.keyboard('{Enter}');
    await waitFor(() => expect(onSend).toHaveBeenCalledWith('preciso de /saudacao agora'));

    await user.type(composer, '/inativa');
    await user.keyboard('{Enter}');
    await waitFor(() => expect(onSend).toHaveBeenLastCalledWith('/inativa'));
  });

  it('should_keep_shift_enter_and_ime_composition_out_of_send_and_expansion_rules', () => {
    const onSend = vi.fn();
    render(<ChatWindow detail={detail} messages={[]} quickMessages={quickMessages} busy={false} error={null} onSend={onSend} onAttach={async () => undefined} />);

    const composer = screen.getByPlaceholderText('Digite uma mensagem...');
    fireEvent.change(composer, { target: { value: '/saudacao' } });
    const shiftEnter = fireEvent.keyDown(composer, { key: 'Enter', shiftKey: true });
    const composingEnter = fireEvent.keyDown(composer, { key: 'Enter', isComposing: true });

    expect(shiftEnter).toBe(true);
    expect(composingEnter).toBe(true);
    expect(composer).toHaveValue('/saudacao');
    expect(onSend).not.toHaveBeenCalled();
  });

  it('should_show_ai_attendance_label_in_header', () => {
    render(
      <ChatWindow
        detail={{ ...detail, tratadoPorIa: true }}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(screen.getByText(/Atendido por IA/)).toBeInTheDocument();
  });

  it('should_show_human_attendant_name_in_header', () => {
    render(
      <ChatWindow
        detail={{
          ...detail,
          tratadoPorIa: false,
          atendentePrincipal: { id: 50, nome: 'Ana Lima', perfil: 'RECEPCIONISTA' },
        }}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(screen.getByText(/Atendido por Ana Lima/)).toBeInTheDocument();
  });

  it('should_not_render_quick_action_buttons_above_message_input', () => {
    render(
      <ChatWindow
        detail={null}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(screen.queryByRole('button', { name: 'Confirmar consulta' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pedir documento' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Enviar localização' })).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText('Digite uma mensagem...')).toBeInTheDocument();
  });

  it('should_insert_quick_message_content_without_sending_automatically', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn();

    render(
      <ChatWindow
        detail={detail}
        messages={[]}
        quickMessages={[
          {
            id: 1,
            categoriaId: null,
            categoriaRotulo: null,
            categoriaCor: null,
            titulo: 'Confirmar consulta',
            atalho: '/confirmar',
            conteudo: 'Sua consulta esta confirmada.',
            ativo: true,
            criadoEm: null,
            atualizadoEm: null,
          },
          {
            id: 2,
            categoriaId: null,
            categoriaRotulo: null,
            categoriaCor: null,
            titulo: 'Inativa',
            atalho: '/inativa',
            conteudo: 'Nao usar.',
            ativo: false,
            criadoEm: null,
            atualizadoEm: null,
          },
        ]}
        busy={false}
        error={null}
        onSend={onSend}
        onAttach={async () => undefined}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Mensagens rápidas' }));
    await user.type(screen.getByLabelText('Buscar mensagens rápidas'), 'confirmar');
    await user.click(screen.getByRole('option', { name: /Confirmar consulta/ }));

    expect(screen.getByPlaceholderText('Digite uma mensagem...')).toHaveValue('Sua consulta esta confirmada.');
    expect(onSend).not.toHaveBeenCalled();
    expect(screen.queryByText('Inativa')).not.toBeInTheDocument();
  });

  it('should_select_the_first_quick_message_with_enter_without_sending', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn();
    const messages = [{ ...quickMessages[0], titulo: 'One Piece', atalho: '/kkkk', conteudo: 'Orientado TGG' }];
    render(<ChatWindow detail={detail} messages={[]} quickMessages={messages} busy={false} error={null} onSend={onSend} onAttach={async () => undefined} />);

    await user.click(screen.getByRole('button', { name: 'Mensagens rápidas' }));
    const search = screen.getByRole('combobox', { name: 'Buscar mensagens rápidas' });
    await user.type(search, 'one piece');
    const option = screen.getByRole('option', { name: /One Piece/ });
    expect(option).toHaveAttribute('aria-selected', 'true');
    await user.keyboard('{Enter}');

    const composer = screen.getByPlaceholderText('Digite uma mensagem...');
    expect(composer).toHaveValue('Orientado TGG');
    expect(composer).toHaveFocus();
    expect(screen.queryByRole('combobox', { name: 'Buscar mensagens rápidas' })).not.toBeInTheDocument();
    expect(onSend).not.toHaveBeenCalled();
    await user.keyboard('{Enter}');
    expect(onSend).toHaveBeenCalledWith('Orientado TGG');
  });

  it('should_navigate_quick_messages_with_arrows_and_select_the_active_option', () => {
    const onSend = vi.fn();
    const messages = [
      { ...quickMessages[0], id: 101, titulo: 'Primeira', atalho: '/primeira', conteudo: 'Conteúdo um' },
      { ...quickMessages[0], id: 102, titulo: 'Segunda', atalho: '/segunda', conteudo: 'Conteúdo dois' },
    ];
    render(<ChatWindow detail={detail} messages={[]} quickMessages={messages} busy={false} error={null} onSend={onSend} onAttach={async () => undefined} />);

    fireEvent.click(screen.getByRole('button', { name: 'Mensagens rápidas' }));
    const search = screen.getByRole('combobox', { name: 'Buscar mensagens rápidas' });
    expect(screen.getByRole('option', { name: /Primeira/ })).toHaveAttribute('aria-selected', 'true');
    fireEvent.keyDown(search, { key: 'ArrowDown' });
    expect(screen.getByRole('option', { name: /Segunda/ })).toHaveAttribute('aria-selected', 'true');
    fireEvent.keyDown(search, { key: 'Enter' });
    expect(screen.getByPlaceholderText('Digite uma mensagem...')).toHaveValue('Conteúdo dois');
    expect(onSend).not.toHaveBeenCalled();
  });

  it('should_keep_the_panel_open_when_enter_has_no_result_and_close_with_escape', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn();
    render(<ChatWindow detail={detail} messages={[]} quickMessages={quickMessages} busy={false} error={null} onSend={onSend} onAttach={async () => undefined} />);
    await user.click(screen.getByRole('button', { name: 'Mensagens rápidas' }));
    const search = screen.getByRole('combobox', { name: 'Buscar mensagens rápidas' });
    await user.type(search, 'inexistente');
    await user.keyboard('{Enter}');
    expect(screen.getByRole('combobox', { name: 'Buscar mensagens rápidas' })).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Digite uma mensagem...')).toHaveValue('');
    expect(onSend).not.toHaveBeenCalled();
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('combobox', { name: 'Buscar mensagens rápidas' })).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText('Digite uma mensagem...')).toHaveFocus();
  });

  it('should_render_image_using_bff_endpoint', () => {
    const mockImageMessage: MensagemAtendimento = {
      id: 1,
      direcao: 'ENTRADA',
      remetente: 'PACIENTE',
      tipoMedia: 'IMAGEM',
      conteudo: '[IMAGEM]',
      conteudoPrevia: '[IMAGEM]',
      whatsappStatus: 'RECEBIDA',
      motivoFalha: null,
      dataHora: new Date().toISOString(),
      entregueEm: null,
      lidaEm: null,
      midia: {
        tipoMedia: 'IMAGEM',
        mimeType: 'image/png',
        nomeArquivo: 'exame.png',
        tamanhoBytes: 1234,
        url: '/api/atendimentos/30/mensagens/1/midia',
      },
      templateNome: null,
      templateIdioma: null,
    };

    render(
      <ChatWindow
        detail={null}
        messages={[mockImageMessage]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    const imgElement = screen.getByRole('img', { name: 'exame.png' });
    expect(imgElement).toBeInTheDocument();
    expect(imgElement).toHaveAttribute('src', '/api/atendimentos/30/mensagens/1/midia');
  });

  it.each(['IMAGEM', 'OUTRO', 'DOCUMENTO'])('should_render_legacy_webp_%s_inline_as_a_sticker', (tipoMedia) => {
    const sticker: MensagemAtendimento = {
      id: 11,
      direcao: 'ENTRADA',
      remetente: 'PACIENTE',
      tipoMedia: 'IMAGEM',
      conteudo: '[IMAGEM] figurinha.webp',
      conteudoPrevia: '[IMAGEM] figurinha.webp',
      whatsappStatus: 'RECEBIDA',
      motivoFalha: null,
      dataHora: new Date().toISOString(),
      entregueEm: null,
      lidaEm: null,
      midia: {
        tipoMedia,
        mimeType: 'image/webp',
        nomeArquivo: 'outro',
        tamanhoBytes: 1234,
        url: '/api/atendimentos/30/mensagens/11/midia',
      },
      templateNome: null,
      templateIdioma: null,
    };

    render(<ChatWindow detail={null} messages={[sticker]} quickMessages={[]} busy={false} error={null} onSend={async () => undefined} onAttach={async () => undefined} />);

    const image = screen.getByRole('img', { name: 'Figurinha recebida' });
    expect(image.closest('a')).toHaveAttribute('href', '/api/atendimentos/30/mensagens/11/midia');
    expect(screen.queryByRole('link', { name: 'outro' })).not.toBeInTheDocument();
  });

  it('should_show_a_sticker_specific_error_without_replacing_the_original_link', () => {
    const sticker: MensagemAtendimento = {
      id: 12, direcao: 'ENTRADA', remetente: 'PACIENTE', tipoMedia: 'IMAGEM',
      conteudo: '[IMAGEM]', conteudoPrevia: '[IMAGEM]', whatsappStatus: 'RECEBIDA', motivoFalha: null,
      dataHora: new Date().toISOString(), entregueEm: null, lidaEm: null,
      midia: { tipoMedia: 'OUTRO', mimeType: 'image/webp', nomeArquivo: 'outro', tamanhoBytes: 1234, url: '/api/atendimentos/30/mensagens/12/midia' },
      templateNome: null, templateIdioma: null,
    };
    render(<ChatWindow detail={null} messages={[sticker]} quickMessages={[]} busy={false} error={null} onSend={async () => undefined} onAttach={async () => undefined} />);

    fireEvent.error(screen.getByRole('img', { name: 'Figurinha recebida' }));
    expect(screen.getByText('Figurinha indisponível')).toBeInTheDocument();
  });

  it('should_preserve_composed_emoji_when_sending_and_use_the_native_emoji_font_stack', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn();
    const unicode = 'Família \u{1F468}\u200D\u{1F469}\u200D\u{1F467}\u200D\u{1F466} ❤️ \u{1F44D}\u{1F3FD}';
    render(<ChatWindow detail={detail} messages={[{ ...makeMessage(13, 'ENTRADA'), conteudo: unicode }]} quickMessages={[]} busy={false} error={null} onSend={onSend} onAttach={async () => undefined} />);

    const composer = screen.getByPlaceholderText('Digite uma mensagem...');
    expect((composer as HTMLElement).style.fontFamily).toContain('Segoe UI Emoji');
    expect((screen.getByText(unicode) as HTMLElement).style.fontFamily).toContain('Segoe UI Emoji');
    await user.type(composer, unicode);
    await user.keyboard('{Enter}');
    expect(onSend).toHaveBeenCalledWith(unicode);
  });

  it('should_render_audio_using_bff_endpoint', () => {
    const mockAudioMessage: MensagemAtendimento = {
      id: 2,
      direcao: 'ENTRADA',
      remetente: 'PACIENTE',
      tipoMedia: 'AUDIO',
      conteudo: '[AUDIO]',
      conteudoPrevia: '[AUDIO]',
      whatsappStatus: 'RECEBIDA',
      motivoFalha: null,
      dataHora: new Date().toISOString(),
      entregueEm: null,
      lidaEm: null,
      midia: {
        tipoMedia: 'AUDIO',
        mimeType: 'audio/ogg',
        nomeArquivo: 'audio.ogg',
        tamanhoBytes: 5678,
        url: '/api/atendimentos/30/mensagens/2/midia',
      },
      templateNome: null,
      templateIdioma: null,
    };

    render(
      <ChatWindow
        detail={null}
        messages={[mockAudioMessage]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    const audioElement = document.querySelector('audio');
    expect(audioElement).toBeInTheDocument();
    expect(audioElement).toHaveAttribute('src', '/api/atendimentos/30/mensagens/2/midia');
  });

  it('should_render_document_link_using_bff_endpoint', () => {
    const mockDocMessage: MensagemAtendimento = {
      id: 3,
      direcao: 'ENTRADA',
      remetente: 'PACIENTE',
      tipoMedia: 'DOCUMENTO',
      conteudo: '[DOCUMENTO]',
      conteudoPrevia: '[DOCUMENTO]',
      whatsappStatus: 'RECEBIDA',
      motivoFalha: null,
      dataHora: new Date().toISOString(),
      entregueEm: null,
      lidaEm: null,
      midia: {
        tipoMedia: 'DOCUMENTO',
        mimeType: 'application/pdf',
        nomeArquivo: 'exame.pdf',
        tamanhoBytes: 9999,
        url: '/api/atendimentos/30/mensagens/3/midia',
      },
      templateNome: null,
      templateIdioma: null,
    };

    render(
      <ChatWindow
        detail={null}
        messages={[mockDocMessage]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    const docLink = screen.getByRole('link', { name: 'exame.pdf' });
    expect(docLink).toBeInTheDocument();
    expect(docLink).toHaveAttribute('href', '/api/atendimentos/30/mensagens/3/midia');
  });

  it('should_keep_24_hour_failure_visible_with_a_friendly_reason', () => {
    render(
      <ChatWindow
        detail={detail}
        messages={[{
          ...makeMessage(5, 'SAIDA'),
          whatsappStatus: 'FALHA',
          motivoFalha: 'A Meta exige template aprovado para responder fora da janela de 24h.',
        }]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(screen.getByText(/janela de atendimento de 24 horas foi encerrada/i)).toBeInTheDocument();
    expect(screen.getByText('Mensagem 5')).toBeInTheDocument();
  });

  it('should_show_friendly_error_when_image_loading_fails', () => {
    const mockImageMessage: MensagemAtendimento = {
      id: 4,
      direcao: 'ENTRADA',
      remetente: 'PACIENTE',
      tipoMedia: 'IMAGEM',
      conteudo: '[IMAGEM]',
      conteudoPrevia: '[IMAGEM]',
      whatsappStatus: 'RECEBIDA',
      motivoFalha: null,
      dataHora: new Date().toISOString(),
      entregueEm: null,
      lidaEm: null,
      midia: {
        tipoMedia: 'IMAGEM',
        mimeType: 'image/png',
        nomeArquivo: 'exame.png',
        tamanhoBytes: 1234,
        url: '/api/atendimentos/30/mensagens/4/midia',
      },
      templateNome: null,
      templateIdioma: null,
    };

    render(
      <ChatWindow
        detail={null}
        messages={[mockImageMessage]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    const imgElement = screen.getByRole('img', { name: 'exame.png' });
    fireEvent.error(imgElement);

    expect(screen.getByText('Imagem indisponível')).toBeInTheDocument();
  });

  it('should_replace_attachment_button_with_accessible_add_menu', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <ChatWindow
        detail={detail}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    const fileInput = container.querySelector<HTMLInputElement>('input[type="file"]');
    expect(fileInput).not.toBeNull();
    const fileClick = vi.spyOn(fileInput!, 'click');
    const add = screen.getByRole('button', { name: 'Adicionar' });

    expect(screen.queryByRole('button', { name: 'Anexar' })).not.toBeInTheDocument();
    expect(add).toHaveAttribute('aria-haspopup', 'menu');
    expect(add).toHaveAttribute('aria-expanded', 'false');
    await user.click(add);
    expect(add).toHaveAttribute('aria-expanded', 'true');
    await user.click(screen.getByRole('menuitem', { name: /Enviar arquivo/ }));

    expect(fileClick).toHaveBeenCalledOnce();
    expect(screen.queryByRole('menuitem', { name: /Enviar arquivo/ })).not.toBeInTheDocument();
  });

  it('should_close_add_menu_with_escape_and_disable_unavailable_templates', async () => {
    const user = userEvent.setup();
    render(
      <ChatWindow
        detail={{ ...detail, whatsappTemplatesDisponiveis: false }}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Adicionar' }));
    const templatesItem = screen.getByRole('menuitem', { name: /Templates/ });
    expect(templatesItem).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByText(/Templates da Meta não estão configurados/)).toBeInTheDocument();
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('menuitem', { name: /Templates/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Adicionar' })).toHaveFocus();
  });

  it('should_keep_uazap_composer_available_without_window_or_templates', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn().mockResolvedValue(undefined);
    render(
      <ChatWindow
        detail={uazapDetail}
        messages={[]}
        quickMessages={quickMessages}
        busy={false}
        error={null}
        onSend={onSend}
        onAttach={async () => undefined}
      />,
    );

    expect(screen.queryByText(/Janela do WhatsApp|24 horas|Nova mensagem/)).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText('Digite uma mensagem...')).toBeEnabled();

    await user.type(screen.getByPlaceholderText('Digite uma mensagem...'), 'Mensagem UAZAP');
    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(onSend).toHaveBeenCalledWith('Mensagem UAZAP');
  });

  it('should_not_mount_or_fetch_meta_templates_for_uazap', async () => {
    const user = userEvent.setup();
    render(
      <ChatWindow
        detail={uazapDetail}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Adicionar' }));
    expect(screen.getByRole('menuitem', { name: /Enviar arquivo/ })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: /Templates/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: 'Enviar template do WhatsApp' })).not.toBeInTheDocument();
    expect(getTemplatesMock).not.toHaveBeenCalled();
  });

  it('should_hide_free_composer_when_window_is_closed_and_open_templates_directly', async () => {
    const user = userEvent.setup();
    render(
      <ChatWindow
        detail={{ ...detail, janelaWhatsappAberta: false }}
        messages={[]}
        quickMessages={quickMessages}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
        onSendTemplate={async () => undefined}
      />,
    );

    expect(screen.getByText(/A sessão de 24 horas para atendimento foi encerrada/)).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('Digite uma mensagem...')).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Janela do WhatsApp aberta/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Mensagens rápidas' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Adicionar' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Nova mensagem' }));
    expect(screen.getByRole('dialog', { name: 'Enviar template do WhatsApp' })).toBeInTheDocument();
    expect(getTemplatesMock).toHaveBeenCalledOnce();
    expect(await screen.findByText('retomar_atendimento')).toBeInTheDocument();
  });

  it('should_explain_when_closed_window_has_no_templates_configured', () => {
    render(
      <ChatWindow
        detail={{
          ...detail,
          janelaWhatsappAberta: false,
          whatsappTemplatesDisponiveis: false,
        }}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(screen.getByText('Templates da Meta não estão configurados para esta clínica.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Nova mensagem' })).toBeDisabled();
    expect(getTemplatesMock).not.toHaveBeenCalled();
  });

  it('should_keep_closed_window_after_template_send_and_reopen_only_from_updated_detail', async () => {
    const user = userEvent.setup();
    const onSendTemplate = vi.fn().mockResolvedValue(undefined);
    const closedDetail = {
      ...detail,
      janelaWhatsappAberta: false,
      aguardandoRespostaTemplate: true,
    };
    const { rerender } = render(
      <ChatWindow
        detail={closedDetail}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
        onSendTemplate={onSendTemplate}
      />,
    );

    expect(screen.getByText(/Template enviado. Aguardando uma resposta/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Nova mensagem' }));
    await screen.findByText('retomar_atendimento');
    await user.click(screen.getByRole('button', { name: 'Enviar' }));
    await waitFor(() => expect(onSendTemplate).toHaveBeenCalledOnce());
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.queryByPlaceholderText('Digite uma mensagem...')).not.toBeInTheDocument();
    expect(screen.getByText(/Aguardando uma resposta/)).toBeInTheDocument();

    rerender(
      <ChatWindow
        detail={{ ...closedDetail, janelaWhatsappAberta: true, aguardandoRespostaTemplate: false }}
        messages={[makeMessage(50, 'ENTRADA')]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
        onSendTemplate={onSendTemplate}
      />,
    );
    expect(screen.getByPlaceholderText('Digite uma mensagem...')).toBeInTheDocument();
    expect(screen.queryByText(/Aguardando uma resposta/)).not.toBeInTheDocument();
  });

  it('should_not_allow_closed_window_to_attach_or_use_quick_messages', async () => {
    const onAttach = vi.fn();
    const { container } = render(
      <ChatWindow
        detail={{ ...detail, janelaWhatsappAberta: false }}
        messages={[]}
        quickMessages={quickMessages}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={onAttach}
      />,
    );
    const input = container.querySelector<HTMLInputElement>('input[type="file"]');
    const file = new File(['conteúdo fictício'], 'arquivo.pdf', { type: 'application/pdf' });

    fireEvent.change(input!, { target: { files: [file] } });

    expect(onAttach).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'Mensagens rápidas' })).not.toBeInTheDocument();
  });

  it('should_clear_typed_text_immediately_when_common_send_is_queued', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn().mockRejectedValue(new Error('Use um template aprovado.'));
    render(
      <ChatWindow
        detail={detail}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={onSend}
        onAttach={async () => undefined}
      />,
    );
    const composer = screen.getByPlaceholderText('Digite uma mensagem...');

    await user.type(composer, 'Texto que não deve sumir');
    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    await waitFor(() => expect(onSend).toHaveBeenCalledWith('Texto que não deve sumir'));
    expect(composer).toHaveValue('');
    expect(composer).toHaveFocus();
  });

  it('should_render_template_metadata_without_regressing_message_content', () => {
    render(
      <ChatWindow
        detail={detail}
        messages={[{
          ...makeMessage(70, 'SAIDA'),
          tipoMedia: 'TEMPLATE',
          conteudo: 'Mensagem de template\ncom duas linhas',
          templateNome: 'retomar_atendimento',
          templateIdioma: 'pt_BR',
        }]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(screen.getByText('Template')).toBeInTheDocument();
    expect(screen.getByText('retomar_atendimento')).toBeInTheDocument();
    expect(screen.getByText(/pt_BR/)).toBeInTheDocument();
    expect(screen.getByText(/Mensagem de template/)).toHaveClass('whitespace-pre-wrap');
  });

  it('should_show_a_natural_expiration_for_today', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-30T10:00:00-03:00'));
    render(
      <ChatWindow
        detail={{ ...detail, janelaWhatsappExpiraEm: '2026-07-30T19:00:00-03:00' }}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(screen.getByText('Janela do WhatsApp aberta · Fecha hoje às 19:00')).toBeInTheDocument();
    vi.useRealTimers();
  });

  it('should_expose_tomorrow_expiration_in_the_accessible_name_and_full_title', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-30T10:00:00-03:00'));
    render(
      <ChatWindow
        detail={{ ...detail, janelaWhatsappExpiraEm: '2026-07-31T22:00:00-03:00' }}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    const indicator = screen.getByLabelText('Janela do WhatsApp aberta. Fecha amanhã às 22:00.');
    expect(indicator).toHaveAttribute('title', 'Janela do WhatsApp disponível até 31/07/2026 às 22:00');
    vi.useRealTimers();
  });

  it('should_refresh_relative_expiration_after_midnight_without_requesting_data', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-30T23:59:30-03:00'));
    render(
      <ChatWindow
        detail={{ ...detail, janelaWhatsappExpiraEm: '2026-07-31T00:30:00-03:00' }}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(screen.getByText('Janela do WhatsApp aberta · Fecha amanhã às 00:30')).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(60_000));
    expect(screen.getByText('Janela do WhatsApp aberta · Fecha hoje às 00:30')).toBeInTheDocument();
    vi.useRealTimers();
  });

  it('should_not_render_negative_or_invalid_expiration_information', () => {
    render(
      <ChatWindow
        detail={{ ...detail, janelaWhatsappExpiraEm: '2020-01-01T10:00:00Z' }}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(screen.getByText('Janela do WhatsApp aberta')).toBeInTheDocument();
    expect(screen.queryByText(/Fecha /)).not.toBeInTheDocument();
  });

  it('should_keep_only_the_open_state_when_the_expiration_is_null_or_invalid', () => {
    const { rerender } = render(
      <ChatWindow
        detail={{ ...detail, janelaWhatsappExpiraEm: null }}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    expect(screen.getByText('Janela do WhatsApp aberta')).toBeInTheDocument();
    expect(screen.queryByText(/Fecha /)).not.toBeInTheDocument();

    rerender(
      <ChatWindow
        detail={{ ...detail, janelaWhatsappExpiraEm: 'data-inválida' }}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    expect(screen.getByText('Janela do WhatsApp aberta')).toBeInTheDocument();
    expect(screen.queryByText(/Fecha /)).not.toBeInTheDocument();
  });

  it('should_scroll_to_latest_message_when_opening_conversation', async () => {
    render(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1), makeMessage(2), makeMessage(3)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    const container = screen.getByTestId('message-scroll-container');
    await waitFor(() => expect(container.scrollTop).toBe(1000));
    expect(scrollIntoViewMock).not.toHaveBeenCalled();
  });

  it('should_scroll_to_latest_message_when_switching_conversation', async () => {
    const { rerender } = render(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    const container = screen.getByTestId('message-scroll-container');
    await waitFor(() => expect(container.scrollTop).toBe(1000));
    container.scrollTop = 0;

    rerender(
      <ChatWindow
        detail={{ ...detail, id: 31, paciente: { ...detail.paciente, id: 11, nome: 'Outra Paciente' } }}
        messages={[makeMessage(10), makeMessage(11)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    await waitFor(() => expect(container.scrollTop).toBe(1000));
    expect(scrollIntoViewMock).not.toHaveBeenCalled();
  });

  it('should_follow_new_messages_when_user_is_near_the_bottom', async () => {
    const { rerender } = render(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1), makeMessage(2)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    const container = screen.getByTestId('message-scroll-container');
    setScrollMetrics(container, { scrollHeight: 1000, clientHeight: 360, scrollTop: 620 });
    fireEvent.scroll(container);

    rerender(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1), makeMessage(2), makeMessage(3)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    await waitFor(() => expect(container.scrollTop).toBe(1000));
    expect(scrollIntoViewMock).not.toHaveBeenCalled();
  });

  it('should_not_force_scroll_when_user_is_reading_old_messages', async () => {
    const { rerender } = render(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1), makeMessage(2)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    const container = screen.getByTestId('message-scroll-container');
    setScrollMetrics(container, { scrollHeight: 1200, clientHeight: 360, scrollTop: 100 });
    fireEvent.scroll(container);

    rerender(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1), makeMessage(2), makeMessage(3)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    await waitFor(() => expect(screen.getByRole('button', { name: 'Ir para o final da conversa' })).toBeInTheDocument());
    expect(container.scrollTop).toBe(100);
    expect(scrollIntoViewMock).not.toHaveBeenCalled();
  });

  it('should_scroll_to_sent_message_even_when_user_was_reading_old_messages', async () => {
    const { rerender } = render(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1), makeMessage(2)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    const container = screen.getByTestId('message-scroll-container');
    setScrollMetrics(container, { scrollHeight: 1200, clientHeight: 360, scrollTop: 100 });
    fireEvent.scroll(container);

    rerender(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1), makeMessage(2), makeMessage(3, 'SAIDA')]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    await waitFor(() => expect(container.scrollTop).toBe(1200));
    expect(scrollToMock).not.toHaveBeenCalled();
    expect(container.scrollTop).toBe(1200);
    expect(scrollIntoViewMock).not.toHaveBeenCalled();
  });

  it('should_not_move_on_rerender_with_the_same_message_ids', () => {
    const messages = [makeMessage(1), makeMessage(2)];
    const { rerender } = render(
      <ChatWindow
        detail={detail}
        messages={messages}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    const container = screen.getByTestId('message-scroll-container');
    setScrollMetrics(container, { scrollHeight: 1200, clientHeight: 360, scrollTop: 120 });
    fireEvent.scroll(container);
    scrollToMock.mockClear();

    rerender(
      <ChatWindow
        detail={{ ...detail, naoLidas: 2 }}
        messages={[...messages]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(container.scrollTop).toBe(120);
    expect(scrollToMock).not.toHaveBeenCalled();
  });

  it('should_not_move_when_polling_only_updates_message_status', () => {
    const initialMessages = [makeMessage(1), makeMessage(2, 'SAIDA')];
    const { rerender } = render(
      <ChatWindow
        detail={detail}
        messages={initialMessages}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    const container = screen.getByTestId('message-scroll-container');
    setScrollMetrics(container, { scrollHeight: 1200, clientHeight: 360, scrollTop: 120 });
    fireEvent.scroll(container);
    scrollToMock.mockClear();

    rerender(
      <ChatWindow
        detail={detail}
        messages={initialMessages.map((message) => ({ ...message, whatsappStatus: 'LIDA' }))}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(container.scrollTop).toBe(120);
    expect(scrollToMock).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'Ir para o final da conversa' })).not.toBeInTheDocument();
  });

  it('should_preserve_visible_position_when_older_messages_are_prepended', () => {
    const { rerender } = render(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(2), makeMessage(3)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    const container = screen.getByTestId('message-scroll-container');
    setScrollMetrics(container, { scrollHeight: 1200, clientHeight: 360, scrollTop: 100 });
    fireEvent.scroll(container);
    Object.defineProperty(container, 'scrollHeight', { configurable: true, value: 1500 });

    rerender(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1), makeMessage(2), makeMessage(3)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(container.scrollTop).toBe(400);
    expect(scrollIntoViewMock).not.toHaveBeenCalled();
  });

  it('should_return_to_bottom_from_new_messages_button', async () => {
    const user = userEvent.setup();
    const { rerender } = render(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    const container = screen.getByTestId('message-scroll-container');
    setScrollMetrics(container, { scrollHeight: 1200, clientHeight: 360, scrollTop: 100 });
    fireEvent.scroll(container);
    rerender(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1), makeMessage(2)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Ir para o final da conversa' }));

    expect(container.scrollTop).toBe(1200);
    expect(screen.queryByRole('button', { name: 'Ir para o final da conversa' })).not.toBeInTheDocument();
  });

  it('should_not_move_old_history_when_media_finishes_loading', () => {
    const imageMessage = {
      ...makeMessage(1),
      tipoMedia: 'IMAGEM' as const,
      midia: {
        tipoMedia: 'IMAGEM' as const,
        url: 'https://media.test/image.jpg',
        nomeArquivo: 'imagem.jpg',
        mimeType: 'image/jpeg',
        tamanhoBytes: 100,
      },
    };
    render(
      <ChatWindow
        detail={detail}
        messages={[imageMessage]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    const container = screen.getByTestId('message-scroll-container');
    setScrollMetrics(container, { scrollHeight: 1200, clientHeight: 360, scrollTop: 100 });
    fireEvent.scroll(container);

    fireEvent.load(screen.getByRole('img', { name: 'imagem.jpg' }));

    expect(container.scrollTop).toBe(100);
  });

  it('should_keep_bottom_when_media_finishes_loading_near_the_end', () => {
    const imageMessage = {
      ...makeMessage(1),
      tipoMedia: 'IMAGEM' as const,
      midia: {
        tipoMedia: 'IMAGEM' as const,
        url: 'https://media.test/image.jpg',
        nomeArquivo: 'imagem.jpg',
        mimeType: 'image/jpeg',
        tamanhoBytes: 100,
      },
    };
    render(
      <ChatWindow
        detail={detail}
        messages={[imageMessage]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    const container = screen.getByTestId('message-scroll-container');
    setScrollMetrics(container, { scrollHeight: 1200, clientHeight: 360, scrollTop: 820 });
    fireEvent.scroll(container);
    Object.defineProperty(container, 'scrollHeight', { configurable: true, value: 1400 });

    fireEvent.load(screen.getByRole('img', { name: 'imagem.jpg' }));

    expect(container.scrollTop).toBe(1400);
  });

  it('should_scroll_after_messages_finish_loading_for_the_open_conversation', () => {
    const { rerender } = render(
      <ChatWindow
        detail={detail}
        messages={[]}
        quickMessages={[]}
        busy={true}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    const container = screen.getByTestId('message-scroll-container');
    container.scrollTop = 0;

    rerender(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1), makeMessage(2)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(container.scrollTop).toBe(1000);
  });

  it('should_only_follow_panel_resize_while_user_is_near_bottom', () => {
    render(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1), makeMessage(2)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    const container = screen.getByTestId('message-scroll-container');
    setScrollMetrics(container, { scrollHeight: 1200, clientHeight: 360, scrollTop: 100 });
    fireEvent.scroll(container);
    scrollToMock.mockClear();

    fireEvent(window, new Event('resize'));

    expect(container.scrollTop).toBe(100);
    expect(scrollToMock).not.toHaveBeenCalled();

    container.scrollTop = 820;
    fireEvent.scroll(container);
    Object.defineProperty(container, 'scrollHeight', { configurable: true, value: 1400 });
    fireEvent(window, new Event('resize'));

    expect(container.scrollTop).toBe(1400);
  });

  it('should_ignore_late_media_callback_from_previous_conversation', () => {
    const imageMessage = {
      ...makeMessage(1),
      tipoMedia: 'IMAGEM' as const,
      midia: {
        tipoMedia: 'IMAGEM' as const,
        url: 'https://media.test/old.jpg',
        nomeArquivo: 'old.jpg',
        mimeType: 'image/jpeg',
        tamanhoBytes: 100,
      },
    };
    const { rerender } = render(
      <ChatWindow
        detail={detail}
        messages={[imageMessage]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    const oldImage = screen.getByRole('img', { name: 'old.jpg' });
    const container = screen.getByTestId('message-scroll-container');

    rerender(
      <ChatWindow
        detail={{ ...detail, id: 31, paciente: { ...detail.paciente, id: 11, nome: 'Outra Paciente' } }}
        messages={[makeMessage(10)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    setScrollMetrics(container, { scrollHeight: 1200, clientHeight: 360, scrollTop: 120 });
    fireEvent.scroll(container);

    fireEvent.load(oldImage);

    expect(container.scrollTop).toBe(120);
  });

  it('should_register_resize_listener_once_and_remove_it_on_unmount', () => {
    const addListenerSpy = vi.spyOn(window, 'addEventListener');
    const removeListenerSpy = vi.spyOn(window, 'removeEventListener');
    const { rerender, unmount } = render(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    rerender(
      <ChatWindow
        detail={{ ...detail, id: 31 }}
        messages={[makeMessage(2)]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );
    unmount();

    const resizeAdds = addListenerSpy.mock.calls.filter(([event]) => event === 'resize');
    const resizeRemovals = removeListenerSpy.mock.calls.filter(([event]) => event === 'resize');
    expect(resizeAdds).toHaveLength(1);
    expect(resizeRemovals).toHaveLength(1);
    expect(resizeRemovals[0]?.[1]).toBe(resizeAdds[0]?.[1]);
  });

  it('should_free_chat_content_from_the_880px_bottleneck', () => {
    const { container } = render(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1, 'ENTRADA'), makeMessage(2, 'SAIDA')]}
        quickMessages={quickMessages}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    // O gargalo fixo de 880px foi removido de todas as áreas do chat.
    expect(container.querySelectorAll('.max-w-\\[880px\\]')).toHaveLength(0);
    // A lista de mensagens usa a largura fluida (cap só em ultrawide).
    const list = screen.getByTestId('message-scroll-container').firstElementChild;
    expect(list).toHaveClass('mx-auto', 'w-full', 'max-w-[1600px]');
  });

  it('should_make_the_composer_follow_the_fluid_width', () => {
    render(
      <ChatWindow
        detail={detail}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    const composer = screen.getByPlaceholderText('Digite uma mensagem...');
    expect(composer.closest('.max-w-\\[1600px\\]')).not.toBeNull();
    expect(composer.closest('.max-w-\\[880px\\]')).toBeNull();
    // min-w-0 permite o textarea encolher sem gerar barra horizontal.
    expect(composer).toHaveClass('min-w-0', 'flex-1');
  });

  it('should_make_the_quick_message_selector_follow_the_fluid_width', async () => {
    const user = userEvent.setup();
    render(
      <ChatWindow
        detail={detail}
        messages={[]}
        quickMessages={quickMessages}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Mensagens rápidas' }));
    const search = screen.getByLabelText('Buscar mensagens rápidas');
    expect(search.closest('.max-w-\\[1600px\\]')).not.toBeNull();
    expect(search.closest('.max-w-\\[880px\\]')).toBeNull();
  });

  it('should_make_the_closed_window_notice_follow_the_fluid_width', () => {
    render(
      <ChatWindow
        detail={{ ...detail, janelaWhatsappAberta: false }}
        messages={[]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    const notice = screen.getByText(/A sessão de 24 horas para atendimento foi encerrada/);
    expect(notice.closest('.max-w-\\[1600px\\]')).not.toBeNull();
    expect(notice.closest('.max-w-\\[880px\\]')).toBeNull();
  });

  it('should_align_incoming_left_outgoing_right_with_an_adaptive_bubble_cap', () => {
    render(
      <ChatWindow
        detail={detail}
        messages={[makeMessage(1, 'ENTRADA'), makeMessage(2, 'SAIDA')]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    // Recebidas à esquerda, enviadas à direita.
    expect(screen.getByText('Mensagem 1').closest('.flex-col')).toHaveClass('items-start');
    expect(screen.getByText('Mensagem 2').closest('.flex-col')).toHaveClass('items-end');
    // Limite adaptativo (percentual + cap de leitura) e quebra segura de palavras/URLs.
    const bubble = screen.getByText('Mensagem 1').closest('.break-words');
    expect(bubble).toHaveClass('max-w-[min(88%,760px)]', 'break-words');
  });

  it('should_render_ai_handoff_summary_as_a_distinct_internal_card', () => {
    const summary: MensagemAtendimento = {
      ...makeMessage(12, 'SISTEMA'),
      remetente: 'IA',
      tipoMedia: 'AI_HANDOFF_SUMMARY',
      conteudo: 'Paciente pediu retorno com a recepção.',
      conteudoPrevia: 'Paciente pediu retorno com a recepção.',
      whatsappStatus: 'INTERNO',
    };

    render(
      <ChatWindow
        detail={detail}
        messages={[summary]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(screen.getByTestId('ai-handoff-summary')).toBeInTheDocument();
    expect(screen.getByText('Resumo da IA para o atendimento')).toBeInTheDocument();
    expect(screen.getByText('Paciente pediu retorno com a recepção.')).toBeInTheDocument();
    expect(screen.queryByText('Falha no envio')).not.toBeInTheDocument();
  });

  it('should_render_handoff_markers_without_treating_them_as_whatsapp_messages', () => {
    const ended: MensagemAtendimento = {
      ...makeMessage(13, 'SISTEMA'),
      remetente: 'SISTEMA',
      tipoMedia: 'AI_HANDOFF_ENDED',
      conteudo: 'Fim das mensagens com a IA',
      whatsappStatus: 'INTERNO',
    };
    const started: MensagemAtendimento = {
      ...makeMessage(14, 'SISTEMA'),
      remetente: 'SISTEMA',
      tipoMedia: 'HUMAN_HANDOFF_START',
      conteudo: 'Atendimento #30 transferido para humano',
      whatsappStatus: 'INTERNO',
    };

    render(<ChatWindow detail={detail} messages={[ended, started]} quickMessages={[]} busy={false} error={null} onSend={async () => undefined} onAttach={async () => undefined} />);

    expect(screen.getByTestId('handoff-event-ai_handoff_ended')).toHaveTextContent('Fim das mensagens com a IA');
    expect(screen.getByTestId('handoff-event-human_handoff_start')).toHaveTextContent('Atendimento #30 transferido para humano');
    expect(screen.queryByTestId('status-icon')).not.toBeInTheDocument();
  });

  it('should_not_show_a_pending_clock_for_ai_messages_registered_after_external_send', () => {
    const message: MensagemAtendimento = {
      ...makeMessage(15, 'SAIDA'),
      remetente: 'IA',
      whatsappStatus: 'REGISTRADA',
    };
    render(<ChatWindow detail={detail} messages={[message]} quickMessages={[]} busy={false} error={null} onSend={async () => undefined} onAttach={async () => undefined} />);

    expect(screen.getByText('IA')).toBeInTheDocument();
    expect(screen.queryByTestId('status-icon')).not.toBeInTheDocument();
  });

  it('should_keep_delivery_status_distinct_from_ai_and_attendant_authorship', () => {
    const messages: MensagemAtendimento[] = [
      { ...makeMessage(16, 'SAIDA'), remetente: 'IA', whatsappStatus: 'PENDENTE' },
      { ...makeMessage(17, 'SAIDA'), remetente: 'ATENDENTE', whatsappStatus: 'ENVIADA' },
      { ...makeMessage(18, 'SAIDA'), remetente: 'ATENDENTE', whatsappStatus: 'ENTREGUE' },
      { ...makeMessage(19, 'SAIDA'), remetente: 'IA', whatsappStatus: 'LIDA' },
      { ...makeMessage(20, 'SAIDA'), remetente: 'IA', whatsappStatus: 'FALHA' },
    ];
    render(<ChatWindow detail={detail} messages={messages} quickMessages={[]} busy={false} error={null} onSend={async () => undefined} onAttach={async () => undefined} />);

    expect(screen.getAllByText('IA')).toHaveLength(3);
    expect(screen.getAllByText('Atendente')).toHaveLength(2);
    expect(screen.getAllByTestId('status-icon')).toHaveLength(5);
  });

  it('should_prevent_media_from_overflowing_the_bubble', () => {
    const imageMessage: MensagemAtendimento = {
      ...makeMessage(9, 'ENTRADA'),
      tipoMedia: 'IMAGEM',
      conteudo: '[IMAGEM]',
      conteudoPrevia: '[IMAGEM]',
      midia: {
        tipoMedia: 'IMAGEM',
        mimeType: 'image/png',
        nomeArquivo: 'largura.png',
        tamanhoBytes: 1234,
        url: '/api/atendimentos/30/mensagens/9/midia',
      },
    };

    render(
      <ChatWindow
        detail={detail}
        messages={[imageMessage]}
        quickMessages={[]}
        busy={false}
        error={null}
        onSend={async () => undefined}
        onAttach={async () => undefined}
      />,
    );

    expect(screen.getByRole('img', { name: 'largura.png' })).toHaveClass('max-w-full');
  });
});
