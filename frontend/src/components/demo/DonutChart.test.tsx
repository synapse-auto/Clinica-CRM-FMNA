import { fireEvent, render, screen } from '@testing-library/react';
import { DonutChart } from './DonutChart';

describe('DonutChart', () => {
  const items = [
    { label: 'Pré-natal', value: 35, color: '#008f95' },
    { label: 'Exames', value: 65, color: '#2563eb' },
  ];

  it('should_show_name_value_and_percentage_when_slice_is_hovered', () => {
    render(<DonutChart items={items} />);

    const slice = screen.getByLabelText('Pré-natal: 35 (35%)');
    fireEvent.pointerEnter(slice);

    expect(slice).toHaveAttribute('data-active', 'true');
    expect(screen.getByRole('tooltip')).toHaveTextContent('Pré-natal');
    expect(screen.getByRole('tooltip')).toHaveTextContent('35');
    expect(screen.getByRole('tooltip')).toHaveTextContent('35%');
  });

  it('should_expose_entry_animation_on_each_slice', () => {
    render(<DonutChart items={items} />);

    expect(screen.getByLabelText('Pré-natal: 35 (35%)')).toHaveClass('chart-donut-enter');
  });

  it('should_show_raw_values_when_value_mode_is_value', () => {
    render(<DonutChart items={items} valueMode="value" />);

    expect(screen.getByTestId('donut-legend')).toHaveTextContent('Pré-natal35');
    expect(screen.getByTestId('donut-legend')).toHaveTextContent('Exames65');
  });
});
