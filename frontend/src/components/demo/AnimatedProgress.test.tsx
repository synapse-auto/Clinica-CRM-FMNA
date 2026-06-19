import { render, screen } from '@testing-library/react';
import { AnimatedProgress } from './AnimatedProgress';

describe('AnimatedProgress', () => {
  it('should_render_an_accessible_animated_percentage', () => {
    render(<AnimatedProgress value={67} label="Uso da tag Plano de Saúde" className="bg-clinic-orange" />);

    const progress = screen.getByRole('progressbar', { name: 'Uso da tag Plano de Saúde' });
    expect(progress).toHaveAttribute('aria-valuenow', '67');
    expect(progress).toHaveStyle({ '--progress-value': '0.67' });
    expect(progress).toHaveClass('chart-progress-enter');
  });
});
