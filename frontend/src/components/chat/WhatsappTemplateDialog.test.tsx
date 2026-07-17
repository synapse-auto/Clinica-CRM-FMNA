import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AtendimentoApiError, getWhatsappTemplates } from '@/services/atendimentos';
import type { WhatsappTemplate } from '@/types/atendimento';
import {
  normalizeTemplateSearch,
  renderComponentText,
  templateParameterKey,
  WhatsappTemplateDialog,
} from './WhatsappTemplateDialog';

vi.mock('@/services/atendimentos', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/services/atendimentos')>();
  return { ...actual, getWhatsappTemplates: vi.fn() };
});

const getTemplatesMock = vi.mocked(getWhatsappTemplates);

const approvedTemplate: WhatsappTemplate = {
  id: 'template-1',
  nome: 'confirmação_consulta',
  idioma: 'pt_BR',
  status: 'APPROVED',
  categoria: 'UTILITY',
  cabecalho: 'Olá, {{1}}',
  corpo: 'Sua consulta de {{1}} será em {{2}}.',
  rodape: 'Clínica de teste',
  botoes: [{ tipo: 'URL', texto: 'Ver detalhes', url: 'https://example.test/{{1}}' }],
  variaveis: [
    { componente: 'HEADER', posicao: 1, indiceBotao: null },
    { componente: 'BODY', posicao: 1, indiceBotao: null },
    { componente: 'BODY', posicao: 2, indiceBotao: null },
    { componente: 'BUTTON', posicao: 1, indiceBotao: 0 },
  ],
  suportado: true,
  motivoNaoSuportado: null,
};

const pendingTemplate: WhatsappTemplate = {
  ...approvedTemplate,
  id: 'template-2',
  nome: 'aviso_pendente',
  status: 'PENDING',
  variaveis: [],
};

const unsupportedTemplate: WhatsappTemplate = {
  ...approvedTemplate,
  id: 'template-3',
  nome: 'formato_nao_suportado',
  suportado: false,
  motivoNaoSuportado: 'Cabeçalho de mídia ainda não suportado.',
  variaveis: [],
};

const namedTemplate: WhatsappTemplate = {
  ...approvedTemplate,
  id: 'template-named',
  nome: 'confirmacao_nomeada',
  cabecalho: 'Confirmacao {{vr_titulo}}',
  corpo: 'Ola {{vr_nome}}',
  botoes: [],
  variaveis: [
    { componente: 'HEADER', posicao: 1, indiceBotao: null, nomeParametro: 'vr_titulo' },
    { componente: 'BODY', posicao: 1, indiceBotao: null, nomeParametro: 'vr_nome' },
  ],
};

const staticTemplate: WhatsappTemplate = {
  ...approvedTemplate,
  id: 'template-static',
  nome: 'aviso_estatico',
  cabecalho: null,
  corpo: 'Aviso confirmado',
  botoes: [],
  variaveis: [],
};

beforeEach(() => {
  getTemplatesMock.mockReset();
  getTemplatesMock.mockResolvedValue([approvedTemplate, pendingTemplate, unsupportedTemplate]);
});

describe('WhatsappTemplateDialog', () => {
  it('should_load_only_when_open_and_filter_without_case_or_accents', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn();
    const { rerender } = renderDialog({ open: false, onSend });

    expect(getTemplatesMock).not.toHaveBeenCalled();

    rerender(dialogElement({ open: true, onSend }));
    expect(await screen.findByText('confirmação_consulta')).toBeInTheDocument();
    expect(getTemplatesMock).toHaveBeenCalledOnce();
    expect(screen.getAllByText('Aprovado')).toHaveLength(2);
    expect(screen.getByText('Pendente')).toBeInTheDocument();

    const search = screen.getByRole('searchbox', { name: 'Pesquisar templates' });
    await user.type(search, '  CONFIRMACAO   CONSULTA ');
    expect(screen.getByText('confirmação_consulta')).toBeInTheDocument();
    expect(screen.queryByText('aviso_pendente')).not.toBeInTheDocument();
    await user.keyboard('{Enter}');
    expect(onSend).not.toHaveBeenCalled();
  });

  it('should_show_status_and_reason_for_templates_that_cannot_be_sent', async () => {
    const user = userEvent.setup();
    renderDialog();
    await screen.findByText('confirmação_consulta');

    await user.click(screen.getByRole('button', { name: /aviso_pendente/i }));
    expect(screen.getByText(/está pendente e não pode ser enviado/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Enviar' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: /formato_nao_suportado/i }));
    expect(screen.getByText('Cabeçalho de mídia ainda não suportado.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Enviar' })).toBeDisabled();
  });

  it('should_render_variables_and_safe_preview_then_send_exact_contract_once', async () => {
    const user = userEvent.setup();
    const pendingSend = deferred<void>();
    const onSend = vi.fn(() => pendingSend.promise);
    const onOpenChange = vi.fn();
    renderDialog({ onSend, onOpenChange });

    await screen.findByText('confirmação_consulta');
    const preview = screen.getByRole('region', { name: 'Prévia do template' });
    expect(preview).toHaveTextContent('[variável 1]');
    expect(preview).toHaveTextContent('Clínica de teste');
    expect(preview).toHaveTextContent('Ver detalhes');

    await user.type(screen.getByLabelText('Cabeçalho — variável 1'), '<b>Ana</b>');
    await user.type(screen.getByLabelText('Mensagem — variável 1'), 'Ultrassonografia');
    await user.type(screen.getByLabelText('Mensagem — variável 2'), '15 de julho');
    await user.type(screen.getByLabelText('Botão 1 — complemento da URL'), 'consulta-15');

    expect(preview).toHaveTextContent('<b>Ana</b>');
    expect(preview.querySelector('b')).toBeNull();
    expect(preview).toHaveTextContent('Sua consulta de Ultrassonografia será em 15 de julho.');
    expect(preview).toHaveTextContent('https://example.test/consulta-15');
    expect(within(preview).queryByRole('link')).not.toBeInTheDocument();

    const sendButton = screen.getByRole('button', { name: 'Enviar' });
    fireEvent.click(sendButton);
    fireEvent.click(sendButton);

    expect(onSend).toHaveBeenCalledOnce();
    expect(onSend).toHaveBeenCalledWith({
      nome: 'confirmação_consulta',
      idioma: 'pt_BR',
      parametros: [
        { componente: 'HEADER', posicao: 1, indiceBotao: null, nomeParametro: null, valor: '<b>Ana</b>' },
        { componente: 'BODY', posicao: 1, indiceBotao: null, nomeParametro: null, valor: 'Ultrassonografia' },
        { componente: 'BODY', posicao: 2, indiceBotao: null, nomeParametro: null, valor: '15 de julho' },
        { componente: 'BUTTON', posicao: 1, indiceBotao: 0, nomeParametro: null, valor: 'consulta-15' },
      ],
    });
    expect(screen.getByRole('button', { name: 'Enviando...' })).toBeDisabled();

    pendingSend.resolve();
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  });

  it('should_render_named_variables_in_preview_and_send_their_official_names', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn().mockResolvedValue(undefined);
    getTemplatesMock.mockResolvedValue([namedTemplate]);
    renderDialog({ onSend });

    await screen.findByText('confirmacao_nomeada');
    expect(screen.queryByText(/não precisa de personalização/i)).not.toBeInTheDocument();
    await user.type(screen.getByLabelText(/Cabeçalho.*vr_titulo/), 'Lembrete');
    await user.type(screen.getByLabelText(/Mensagem.*vr_nome/), 'Pessoa ficticia');

    const preview = screen.getByRole('region', { name: /Prévia do template/i });
    expect(preview).toHaveTextContent('Confirmacao Lembrete');
    expect(preview).toHaveTextContent('Ola Pessoa ficticia');
    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(onSend).toHaveBeenCalledWith({
      nome: 'confirmacao_nomeada',
      idioma: 'pt_BR',
      parametros: [
        {
          componente: 'HEADER',
          posicao: 1,
          indiceBotao: null,
          nomeParametro: 'vr_titulo',
          valor: 'Lembrete',
        },
        {
          componente: 'BODY',
          posicao: 1,
          indiceBotao: null,
          nomeParametro: 'vr_nome',
          valor: 'Pessoa ficticia',
        },
      ],
    });
  });

  it('should_send_a_static_template_with_an_empty_parameter_list', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn().mockResolvedValue(undefined);
    getTemplatesMock.mockResolvedValue([staticTemplate]);
    renderDialog({ onSend });

    await screen.findByText('aviso_estatico');
    expect(screen.getByText(/não precisa de personalização/i)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(onSend).toHaveBeenCalledWith({
      nome: 'aviso_estatico',
      idioma: 'pt_BR',
      parametros: [],
    });
  });

  it('should_treat_name_and_language_as_unique_and_clear_incompatible_parameters', async () => {
    const user = userEvent.setup();
    const englishTemplate: WhatsappTemplate = {
      ...approvedTemplate,
      id: 'template-en',
      idioma: 'en_US',
      cabecalho: null,
      corpo: 'Appointment for {{1}}.',
      botoes: [],
      variaveis: [{ componente: 'BODY', posicao: 1, indiceBotao: null }],
    };
    getTemplatesMock.mockResolvedValue([approvedTemplate, englishTemplate]);
    renderDialog();
    await screen.findByText('pt_BR');

    await user.type(screen.getByLabelText('Cabeçalho — variável 1'), 'Valor antigo');
    await user.click(screen.getByRole('button', { name: /confirmação_consulta.*en_US/i }));

    expect(screen.queryByLabelText('Cabeçalho — variável 1')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Mensagem — variável 1')).toHaveValue('');
    expect(screen.getByText('en_US')).toBeInTheDocument();
    expect(screen.getByText('pt_BR')).toBeInTheDocument();
  });

  it('should_validate_blank_variables_and_keep_values_after_send_failure', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn().mockRejectedValue(new Error('Meta indisponível. Tente novamente.'));
    renderDialog({ onSend });
    await screen.findByText('confirmação_consulta');

    await user.click(screen.getByRole('button', { name: 'Enviar' }));
    expect(screen.getAllByText('Preencha esta variável.')).toHaveLength(4);
    expect(onSend).not.toHaveBeenCalled();
    await user.type(screen.getByLabelText(/Cabeçalho — variável 1/), 'Ana');
    await user.type(screen.getByLabelText(/Mensagem — variável 1/), 'Exame');
    await user.type(screen.getByLabelText(/Mensagem — variável 2/), 'Amanhã');
    await user.type(screen.getByLabelText(/Botão 1 — complemento da URL/), 'detalhes');
    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(await screen.findAllByText('Meta indisponível. Tente novamente.')).not.toHaveLength(0);
    expect(screen.getByLabelText(/Mensagem — variável 1/)).toHaveValue('Exame');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('should_keep_list_selection_and_values_on_502_then_clear_only_send_error_on_selection', async () => {
    const user = userEvent.setup();
    const onSend = vi.fn().mockRejectedValue(new AtendimentoApiError(
      'Falha na operação (502)',
      502,
      'WHATSAPP_TEMPLATE_SEND_FAILED',
    ));
    getTemplatesMock.mockResolvedValue([namedTemplate, staticTemplate]);
    renderDialog({ onSend });

    await screen.findByText('confirmacao_nomeada');
    await user.type(screen.getByLabelText(/Cabeçalho.*vr_titulo/), 'Lembrete');
    await user.type(screen.getByLabelText(/Mensagem.*vr_nome/), 'Pessoa ficticia');
    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(await screen.findByText(/A Meta não aceitou o envio deste template/i)).toBeInTheDocument();
    expect(screen.getByText('confirmacao_nomeada')).toBeInTheDocument();
    expect(screen.getByText('aviso_estatico')).toBeInTheDocument();
    expect(screen.getByLabelText(/Mensagem.*vr_nome/)).toHaveValue('Pessoa ficticia');
    expect(screen.queryByRole('button', { name: 'Tentar novamente' })).not.toBeInTheDocument();
    expect(getTemplatesMock).toHaveBeenCalledOnce();

    await user.click(screen.getByRole('button', { name: /aviso_estatico/i }));
    expect(screen.queryByText(/A Meta não aceitou o envio deste template/i)).not.toBeInTheDocument();
    expect(getTemplatesMock).toHaveBeenCalledOnce();
  });

  it('should_offer_retry_and_render_empty_state', async () => {
    const user = userEvent.setup();
    getTemplatesMock
      .mockRejectedValueOnce(new Error('Falha temporária.'))
      .mockResolvedValueOnce([]);
    renderDialog();

    expect(await screen.findAllByText('Falha temporária.')).not.toHaveLength(0);
    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }));
    expect(await screen.findByText('Nenhum template cadastrado na Meta.')).toBeInTheDocument();
    expect(getTemplatesMock).toHaveBeenCalledTimes(2);
  });

  it('should_ignore_a_late_response_from_previous_attendance', async () => {
    const oldResponse = deferred<WhatsappTemplate[]>();
    const newTemplate = { ...approvedTemplate, id: 'new', nome: 'novo_atendimento' };
    getTemplatesMock
      .mockReturnValueOnce(oldResponse.promise)
      .mockResolvedValueOnce([newTemplate]);
    const { rerender } = renderDialog({ atendimentoId: 30 });

    rerender(dialogElement({ atendimentoId: 31 }));
    expect(await screen.findByText('novo_atendimento')).toBeInTheDocument();
    oldResponse.resolve([approvedTemplate]);

    await waitFor(() => expect(screen.queryByText('confirmação_consulta')).not.toBeInTheDocument());
    expect(getTemplatesMock).toHaveBeenNthCalledWith(1, 30);
    expect(getTemplatesMock).toHaveBeenNthCalledWith(2, 31);
  });
});

describe('template preview helpers', () => {
  it('should_normalize_search_and_keep_missing_variables_as_markers', () => {
    expect(normalizeTemplateSearch('  Confirmação_MÉDICA---retorno ')).toBe('confirmacao medica retorno');
    expect(templateParameterKey({ componente: 'BODY', posicao: 2, indiceBotao: null })).toBe('BODY:2:-');
    expect(renderComponentText('Olá {{1}} e {{2}}', 'BODY', null, { 'BODY:1:-': 'Ana' }))
      .toBe('Olá Ana e [variável 2]');
    expect(templateParameterKey({
      componente: 'BODY',
      posicao: 1,
      indiceBotao: null,
      nomeParametro: 'vr_nome',
    })).toBe('BODY:vr_nome:-');
    expect(renderComponentText('Olá {{vr_nome}}', 'BODY', null, {
      'BODY:vr_nome:-': 'Pessoa ficticia',
    })).toBe('Olá Pessoa ficticia');
  });
});

function renderDialog({
  open = true,
  atendimentoId = 30,
  onOpenChange = vi.fn(),
  onSend = vi.fn().mockResolvedValue(undefined),
}: Partial<React.ComponentProps<typeof WhatsappTemplateDialog>> = {}) {
  return render(dialogElement({ open, atendimentoId, onOpenChange, onSend }));
}

function dialogElement({
  open = true,
  atendimentoId = 30,
  onOpenChange = vi.fn(),
  onSend = vi.fn().mockResolvedValue(undefined),
}: Partial<React.ComponentProps<typeof WhatsappTemplateDialog>> = {}) {
  return (
    <WhatsappTemplateDialog
      open={open}
      atendimentoId={atendimentoId}
      onOpenChange={onOpenChange}
      onSend={onSend}
    />
  );
}

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}
