import { fireEvent, render, screen } from '@testing-library/react';
import { LineAreaChart } from './LineAreaChart';

describe('LineAreaChart', () => {
  const data = [
    { label: '08h', value: 12 },
    { label: '12h', value: 24 },
    { label: '16h', value: 18 },
  ];

  it('should_show_tooltip_point_and_guide_when_point_is_hovered', () => {
    render(<LineAreaChart data={data} />);

    fireEvent.pointerEnter(screen.getByLabelText('Mensagens em 12h: 24'));

    expect(screen.getByRole('tooltip')).toHaveTextContent('12h');
    expect(screen.getByRole('tooltip')).toHaveTextContent('Mensagens');
    expect(screen.getByRole('tooltip')).toHaveTextContent('24');
    expect(screen.getByTestId('line-chart-active-point')).toBeInTheDocument();
    expect(screen.getByTestId('line-chart-active-guide')).toBeInTheDocument();
  });

  it('should_expose_entry_animation_classes', () => {
    render(<LineAreaChart data={data} />);

    expect(screen.getByTestId('line-chart-line')).toHaveClass('chart-line-enter');
    expect(screen.getByTestId('line-chart-area')).toHaveClass('chart-area-enter');
  });
});
