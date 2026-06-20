import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ChatWindow } from './ChatWindow';

describe('ChatWindow', () => {
  it('should_not_render_quick_action_buttons_above_message_input', () => {
    render(
      <ChatWindow
        detail={null}
        messages={[]}
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
});
