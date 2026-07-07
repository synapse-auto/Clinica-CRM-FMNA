import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatWindow } from './ChatWindow';
import type { AtendimentoDetalhe, MensagemAtendimento } from '@/types/atendimento';

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
    ultimaInteracaoEm: null,
    requerRevisao: false,
    convenioStatus: null,
    convenioRevisadoEm: null,
    convenioRevisadoPorId: null,
    convenioRevisadoPorNome: null,
  },
  atendentePrincipal: null,
};

const scrollIntoViewMock = vi.fn();

beforeEach(() => {
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
  };
}

function setScrollMetrics(element: HTMLElement, metrics: { scrollHeight: number; clientHeight: number; scrollTop: number }) {
  Object.defineProperty(element, 'scrollHeight', { configurable: true, value: metrics.scrollHeight });
  Object.defineProperty(element, 'clientHeight', { configurable: true, value: metrics.clientHeight });
  Object.defineProperty(element, 'scrollTop', { configurable: true, writable: true, value: metrics.scrollTop });
}

describe('ChatWindow', () => {
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
    await user.click(screen.getByRole('button', { name: /Confirmar consulta/ }));

    expect(screen.getByPlaceholderText('Digite uma mensagem...')).toHaveValue('Sua consulta esta confirmada.');
    expect(onSend).not.toHaveBeenCalled();
    expect(screen.queryByText('Inativa')).not.toBeInTheDocument();
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
