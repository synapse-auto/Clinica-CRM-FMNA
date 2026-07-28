import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { IniciarAtendimentoDialog } from './IniciarAtendimentoDialog';

describe('IniciarAtendimentoDialog', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('should_validate_phone_focus_the_field_and_keep_the_initial_message_optional', async () => {
    const user = userEvent.setup();
    render(
      <IniciarAtendimentoDialog
        open
        onOpenChange={vi.fn()}
        onStarted={vi.fn()}
      />,
    );

    const phone = screen.getByRole('textbox', { name: 'Telefone' });
    expect(phone).toHaveFocus();
    expect(screen.getByRole('button', { name: 'Iniciar atendimento' })).toBeDisabled();

    await user.type(phone, '83999999999');
    expect(screen.getByRole('button', { name: 'Iniciar atendimento' })).toBeEnabled();
    expect(screen.getByRole('textbox', { name: /Primeira mensagem/ })).toHaveValue('');
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

    await user.type(screen.getByRole('textbox', { name: 'Telefone' }), '83999999999');
    await user.type(screen.getByRole('textbox', { name: /Primeira mensagem/ }), 'Olá, teste');
    const submit = screen.getByRole('button', { name: 'Iniciar atendimento' });
    await user.click(submit);
    await user.click(submit);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    resolveRequest?.(jsonResponse({ message: 'Não foi possível iniciar' }, 409));
    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível iniciar');
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

    await user.type(screen.getByRole('textbox', { name: 'Telefone' }), '+5583999999999');
    await user.type(screen.getByRole('textbox', { name: /Primeira mensagem/ }), 'Mensagem inicial');
    await user.click(screen.getByRole('button', { name: 'Iniciar atendimento' }));

    await waitFor(() => expect(onStarted).toHaveBeenCalledWith(
      expect.objectContaining({ atendimentoId: 44 }),
      'Mensagem inicial',
    ));
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
