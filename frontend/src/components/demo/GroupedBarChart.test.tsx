import { fireEvent, render, screen } from '@testing-library/react';
import { GroupedBarChart } from './GroupedBarChart';

describe('GroupedBarChart', () => {
  const series = [
    { label: 'Consultas', color: '#008f95', values: [10, 14] },
    { label: 'Exames', color: '#38bdf8', values: [4, 7] },
  ];

  it('should_keep_legend_and_show_bar_details_when_hovered', () => {
    render(<GroupedBarChart labels={['Seg', 'Ter']} series={series} />);

    expect(screen.getByTestId('bar-chart-legend')).toHaveTextContent('Consultas');
    expect(screen.getByTestId('bar-chart-legend')).toHaveTextContent('Exames');

    const activeBar = screen.getByLabelText('Ter, Consultas: 14');
    fireEvent.pointerEnter(activeBar);

    expect(activeBar).toHaveAttribute('data-active', 'true');
    expect(screen.getByRole('tooltip')).toHaveTextContent('Ter');
    expect(screen.getByRole('tooltip')).toHaveTextContent('Consultas');
    expect(screen.getByRole('tooltip')).toHaveTextContent('14');
  });

  it('should_animate_bars_from_the_baseline', () => {
    render(<GroupedBarChart labels={['Seg', 'Ter']} series={series} />);

    expect(screen.getByLabelText('Seg, Consultas: 10')).toHaveClass('chart-bar-enter');
  });
});
