import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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

const scrollIntoViewMock = vi.fn();

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
  vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback: FrameRequestCallback) => {
    callback(0);
    return 1;
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

  it('should_keep_typed_text_when_backend_rejects_common_send', async () => {
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
    expect(composer).toHaveValue('Texto que não deve sumir');
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

  it('should_not_render_negative_expiration_information', () => {
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
    expect(screen.queryByText(/Disponível até/)).not.toBeInTheDocument();
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

    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalledWith({
      behavior: 'auto',
      block: 'end',
    }));
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
    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalled());
    scrollIntoViewMock.mockClear();

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

    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalledWith({
      behavior: 'auto',
      block: 'end',
    }));
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
    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalled());
    scrollIntoViewMock.mockClear();

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

    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalled());
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
    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalled());
    scrollIntoViewMock.mockClear();

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

    await waitFor(() => expect(screen.getByRole('button', { name: 'Ir para novas mensagens' })).toBeInTheDocument());
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
    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalled());
    scrollIntoViewMock.mockClear();

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

    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalledWith({
      behavior: 'smooth',
      block: 'end',
    }));
  });
});
