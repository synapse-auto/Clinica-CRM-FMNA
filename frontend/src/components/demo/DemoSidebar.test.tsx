import { render, screen } from '@testing-library/react';
import { vi } from 'vitest';
import { DemoSidebar } from './DemoSidebar';

vi.mock('next/navigation', () => ({
  usePathname: () => '/dashboard',
}));

vi.mock('@/components/theme/ThemeProvider', () => ({
  useTheme: () => ({
    theme: 'dark',
    toggleTheme: vi.fn(),
  }),
}));

describe('DemoSidebar', () => {
  it('should_not_render_whatsapp_demo_shortcuts', () => {
    render(
      <DemoSidebar
        clinic={{
          nome: 'Clínica Femina',
          slug: 'clinica-femina',
          tipoClinica: 'PRE_NATAL',
          usaCirurgiasNaAgenda: true,
          followUpAutomatico: true,
        }}
      />,
    );

    expect(screen.queryByText('WhatsApp IA')).not.toBeInTheDocument();
    expect(screen.queryByText('WhatsApp Integrado')).not.toBeInTheDocument();
    expect(screen.queryByText('WhatsApp Demo')).not.toBeInTheDocument();
  });
});
