import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DashboardPeriodFilter } from './DashboardPeriodFilter';

describe('DashboardPeriodFilter', () => {
  it('should_render_one_selected_period_and_keyboard_links', () => {
    render(<DashboardPeriodFilter selected="MES_ATUAL" today="2026-07-17" />);

    const links = screen.getAllByRole('link');
    expect(links).toHaveLength(3);
    expect(screen.getByRole('link', { name: /mês atual, selecionado/i })).toHaveAttribute('aria-current', 'page');
    expect(links.filter((link) => link.getAttribute('aria-current') === 'page')).toHaveLength(1);
    expect(screen.getByRole('link', { name: /semana atual/i })).toHaveAttribute(
      'href',
      '/dashboard?periodo=SEMANA&data=2026-07-17',
    );
    expect(screen.getByRole('link', { name: /mês anterior/i })).toHaveAttribute(
      'href',
      '/dashboard?periodo=MES&data=2026-06-01',
    );
  });
});
