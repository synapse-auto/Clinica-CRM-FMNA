import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { HorizontalBarChart } from './HorizontalBarChart';

const service = (label: string, color: string, values: number[]) => ({ label, color, values });

describe('HorizontalBarChart', () => {
  it('should_render_an_empty_state_for_an_empty_dataset', () => {
    render(<HorizontalBarChart labels={[]} series={[]} emptyMessage="Sem agendamentos no período." />);
    expect(screen.getByTestId('hbar-empty')).toHaveTextContent('Sem agendamentos no período.');
    expect(screen.queryByTestId('hbar-rows')).not.toBeInTheDocument();
  });

  it('should_render_a_single_professional_without_a_multi_series_legend', () => {
    render(
      <HorizontalBarChart
        labels={['Dra. Ana']}
        series={[service('Consultas', 'var(--clinic-primary)', [3])]}
      />,
    );
    const rows = screen.getAllByTestId('hbar-row');
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByText('Dra. Ana')).toBeInTheDocument();
    // Série única: sem legenda redundante.
    expect(screen.queryByTestId('hbar-legend')).not.toBeInTheDocument();
  });

  it('should_render_many_professionals_inside_a_scrollable_area', () => {
    const labels = Array.from({ length: 20 }, (_, index) => `Médico ${index + 1}`);
    const values = labels.map((_, index) => index + 1);
    render(<HorizontalBarChart labels={labels} series={[service('Consultas', 'var(--clinic-primary)', values)]} maxHeight={230} />);
    const list = screen.getByTestId('hbar-rows');
    expect(screen.getAllByTestId('hbar-row')).toHaveLength(20);
    // Scroll interno controlado: altura limitada + overflow vertical, sem esconder linhas.
    expect(list).toHaveClass('overflow-y-auto');
    expect(list).toHaveStyle({ maxHeight: '230px' });
  });

  it('should_keep_long_names_identifiable_via_truncation_and_title', () => {
    const longName = 'ABMAEL PEREIRA MOURA DE ALBUQUERQUE SANTOS';
    render(<HorizontalBarChart labels={[longName]} series={[service('Ultrassom', 'var(--clinic-blue)', [5])]} />);
    const name = screen.getByText(longName);
    // Nome longo permanece legível por truncamento + tooltip acessível (sem sobreposição).
    expect(name).toHaveClass('truncate');
    expect(name).toHaveAttribute('title', longName);
  });

  it('should_render_the_sem_profissional_category', () => {
    render(
      <HorizontalBarChart
        labels={['Dra. Ana', 'Sem profissional']}
        series={[service('Consultas', 'var(--clinic-primary)', [2, 4])]}
      />,
    );
    expect(screen.getByText('Sem profissional')).toBeInTheDocument();
  });

  it('should_render_equal_widths_for_equal_values', () => {
    render(
      <HorizontalBarChart
        labels={['A', 'B']}
        series={[service('Consultas', 'var(--clinic-primary)', [4, 4])]}
      />,
    );
    const fills = screen.getAllByTestId('hbar-fill');
    expect(fills[0]).toHaveStyle({ width: '100%' });
    expect(fills[1]).toHaveStyle({ width: '100%' });
  });

  it('should_render_proportional_widths_for_very_different_values', () => {
    render(
      <HorizontalBarChart
        labels={['Alta', 'Baixa', 'Zero']}
        series={[service('Consultas', 'var(--clinic-primary)', [100, 1, 0])]}
      />,
    );
    const fills = screen.getAllByTestId('hbar-fill');
    expect(fills[0]).toHaveStyle({ width: '100%' });
    // valor 1 sobre max 100 recebe largura mínima visível (>=4%), não some.
    expect(fills[1]).toHaveStyle({ width: '4%' });
    // valor zero não desenha barra.
    expect(fills[2]).toHaveStyle({ width: '0%' });
  });

  it('should_keep_each_value_associated_with_the_correct_professional', () => {
    render(
      <HorizontalBarChart
        labels={['Dra. Ana', 'Dr. Bruno']}
        series={[
          service('Ultrassom', 'var(--clinic-primary)', [7, 2]),
          service('Consulta', 'var(--clinic-blue)', [1, 9]),
        ]}
      />,
    );
    const rows = screen.getAllByTestId('hbar-row');
    // multi-série: legenda presente e valores por linha corretos.
    expect(screen.getByTestId('hbar-legend')).toBeInTheDocument();
    const ana = within(rows[0]);
    expect(ana.getByText('Dra. Ana')).toBeInTheDocument();
    expect(ana.getByTitle('Dra. Ana · Ultrassom: 7')).toBeInTheDocument();
    expect(ana.getByTitle('Dra. Ana · Consulta: 1')).toBeInTheDocument();
    const bruno = within(rows[1]);
    expect(bruno.getByTitle('Dr. Bruno · Ultrassom: 2')).toBeInTheDocument();
    expect(bruno.getByTitle('Dr. Bruno · Consulta: 9')).toBeInTheDocument();
  });

  it('should_expose_bars_as_accessible_text_not_color_only', () => {
    render(<HorizontalBarChart labels={['Dra. Ana']} series={[service('Consultas', 'var(--clinic-primary)', [3])]} />);
    // Valor comunicado por texto (não apenas cor).
    expect(screen.getByText('3')).toBeInTheDocument();
    // A barra visual em si é decorativa.
    expect(screen.getByTestId('hbar-fill').parentElement).toHaveAttribute('aria-hidden', 'true');
  });
});
