import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { DatePicker } from './date-picker';
import { FormSelect } from './form-select';

describe('form controls', () => {
  it('selects an option with keyboard-accessible shadcn-style select control', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    render(
      <FormSelect
        aria-label="Perfil"
        defaultValue="RECEPCIONISTA"
        onValueChange={onValueChange}
        emptyOptionLabel="Sem perfil"
        options={[
          { value: 'GESTOR', label: 'Gestor' },
          { value: 'RECEPCIONISTA', label: 'Recepcionista' },
        ]}
      />,
    );

    await user.click(screen.getByLabelText('Perfil'));
    expect(screen.getByRole('option', { name: 'Sem perfil' })).toBeInTheDocument();
    await user.click(screen.getByRole('option', { name: 'Gestor' }));

    expect(onValueChange).toHaveBeenCalledWith('GESTOR');
  });

  it('keeps disabled state and presents local dates in pt-BR without changing the API value', () => {
    const onValueChange = vi.fn();
    render(
      <>
        <FormSelect aria-label="Desativado" disabled options={[{ value: 'A', label: 'A' }]} />
        <DatePicker aria-label="Data" value="2026-07-15" onValueChange={onValueChange} />
      </>,
    );

    expect(screen.getByLabelText('Desativado')).toBeDisabled();
    expect(screen.getByLabelText('Data')).toHaveTextContent('15/07/2026');
    expect(onValueChange).not.toHaveBeenCalled();
  });
});
