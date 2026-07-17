import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Switch } from './switch';

describe('Switch', () => {
  it('should_toggle_boolean_value_once_with_mouse_and_keyboard', async () => {
    const user = userEvent.setup();
    const onCheckedChange = vi.fn();
    render(<Switch label="Ativo" defaultChecked={false} onCheckedChange={onCheckedChange} />);

    const control = screen.getByRole('switch', { name: 'Ativo' });
    expect(control).toHaveAttribute('aria-checked', 'false');

    await user.click(control);
    expect(onCheckedChange).toHaveBeenCalledWith(true, expect.anything());
    expect(control).toHaveAttribute('aria-checked', 'true');

    fireEvent.keyDown(control, { key: ' ', code: 'Space' });
    fireEvent.keyUp(control, { key: ' ', code: 'Space' });
    expect(onCheckedChange).toHaveBeenLastCalledWith(false, expect.anything());
    expect(control).toHaveAttribute('aria-checked', 'false');
  });

  it('should_not_toggle_when_disabled', async () => {
    const user = userEvent.setup();
    const onCheckedChange = vi.fn();
    render(<Switch label="Ativo" defaultChecked disabled onCheckedChange={onCheckedChange} />);

    const control = screen.getByRole('switch', { name: 'Ativo' });
    expect(control).toHaveAttribute('aria-disabled', 'true');
    await user.click(control);
    expect(onCheckedChange).not.toHaveBeenCalled();
  });
});
