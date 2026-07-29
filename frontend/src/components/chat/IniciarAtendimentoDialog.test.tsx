import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { IniciarAtendimentoDialog } from './IniciarAtendimentoDialog';

describe('IniciarAtendimentoDialog', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('should_require_name_and_phone_focus_name_and_keep_the_initial_message_optional', async () => {
    const user = userEvent.setup();
    render(
      <IniciarAtendimentoDialog
        open
        onOpenChange={vi.fn()}
        onStarted={vi.fn()}
      />,
    );

    const name = screen.getByRole('textbox', { name: 'Nome do contato' });
    const phone = screen.getByRole('textbox', { name: 'Telefone' });
    expect(name).toHaveFocus();
    expect(screen.getByRole('button', { name: 'Iniciar atendimento' })).toBeDisabled();

    await user.type(name, 'Maria Teste');
    await user.type(phone, '83999999999');
    expect(screen.getByRole('button', { name: 'Iniciar atendimento' })).toBeEnabled();
    expect(screen.getByRole('textbox', { name: /Primeira mensagem/ })).toHaveValue('');
  });

  it('should_show_specific_validation_messages_for_name_and_phone', async () => {
    const user = userEvent.setup();
    render(
      <IniciarAtendimentoDialog
        open
        onOpenChange={vi.fn()}
        onStarted={vi.fn()}
      />,
    );

    const name = screen.getByRole('textbox', { name: 'Nome do contato' });
    const phone = screen.getByRole('textbox', { name: 'Telefone' });
    await user.click(name);
    await user.tab();
    expect(screen.getByText('Nome do contato é obrigatório.')).toBeVisible();

    await user.click(phone);
    await user.tab();
    expect(screen.getByText('Telefone é obrigatório.')).toBeVisible();
  });

  it('should_preserve_fields_on_failure_and_prevent_duplicate_submission', async () => {
    const user = userEvent.setup();
    let resolveRequest: ((response: Response) => void) | undefined;
    const fetchMock = vi.fn(() => new Promise<Response>((resolve) => {
      resolveRequest = resolve;
    }));
    vi.stubGlobal('fetch', fetchMock);
    render(
      <IniciarAtendimentoDialog
        open
        onOpenChange={vi.fn()}
        onStarted={vi.fn()}
      />,
    );

    await user.type(screen.getByRole('textbox', { name: 'Nome do contato' }), 'Maria Teste');
    await user.type(screen.getByRole('textbox', { name: 'Telefone' }), '83999999999');
    await user.type(screen.getByRole('textbox', { name: /Primeira mensagem/ }), 'Olá, teste');
    expect(screen.getByRole('textbox', { name: 'Nome do contato' })).toHaveValue('Maria Teste');
    const submit = screen.getByRole('button', { name: 'Iniciar atendimento' });
    await user.click(submit);
    await user.click(submit);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    resolveRequest?.(jsonResponse({ message: 'Não foi possível iniciar' }, 409));
    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível iniciar');
    expect(screen.getByRole('textbox', { name: 'Nome do contato' })).toHaveValue('Maria Teste');
    expect(screen.getByRole('textbox', { name: 'Telefone' })).toHaveValue('(83) 99999-9999');
    expect(screen.getByRole('textbox', { name: /Primeira mensagem/ })).toHaveValue('Olá, teste');
  });

  it('should_create_first_then_delegate_the_optional_message_and_close', async () => {
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    const onStarted = vi.fn().mockResolvedValue(undefined);
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      atendimentoId: 44,
      pacienteId: 10,
      modo: 'HUMANO',
      atendimento: { id: 44 },
    }));
    vi.stubGlobal('fetch', fetchMock);
    render(
      <IniciarAtendimentoDialog
        open
        onOpenChange={onOpenChange}
        onStarted={onStarted}
      />,
    );

    await user.type(screen.getByRole('textbox', { name: 'Nome do contato' }), 'Maria Teste');
    await user.type(screen.getByRole('textbox', { name: 'Telefone' }), '+5583999999999');
    await user.type(screen.getByRole('textbox', { name: /Primeira mensagem/ }), 'Mensagem inicial');
    await user.click(screen.getByRole('button', { name: 'Iniciar atendimento' }));

    await waitFor(() => expect(onStarted).toHaveBeenCalledWith(
      expect.objectContaining({ atendimentoId: 44 }),
      'Mensagem inicial',
    ));
    expect(JSON.parse(String((fetchMock.mock.calls[0][1] as RequestInit).body))).toEqual({
      nome: 'Maria Teste',
      telefone: '+5583999999999',
    });
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it('should_close_with_escape', async () => {
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    render(
      <IniciarAtendimentoDialog
        open
        onOpenChange={onOpenChange}
        onStarted={vi.fn()}
      />,
    );

    expect(screen.getByRole('textbox', { name: 'Nome do contato' })).toHaveFocus();
    await user.keyboard('{Escape}');
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });
});

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
