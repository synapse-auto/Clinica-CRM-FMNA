import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { SearchableSelect } from './form-select';

describe('SearchableSelect', () => {
  it('filters small option lists without accents and with words out of order', async () => {
    const user = userEvent.setup();
    render(
      <SearchableSelect
        aria-label="Selecionar atendente"
        options={[
          { value: '1', label: 'Joao da Silva' },
          { value: '2', label: 'Maria Costa' },
        ]}
      />,
    );

    const input = screen.getByRole('combobox', { name: 'Selecionar atendente' });
    await user.type(input, 'silva joao');

    expect(await screen.findByText('Joao da Silva')).toBeInTheDocument();
    expect(screen.queryByText('Maria Costa')).not.toBeInTheDocument();
  });
});
