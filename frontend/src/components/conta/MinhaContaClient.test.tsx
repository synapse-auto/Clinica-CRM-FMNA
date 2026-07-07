import { render, screen } from '@testing-library/react';
import { MinhaContaClient } from './MinhaContaClient';

const user = {
  id: 3,
  nome: 'Dra. Ana Silva',
  email: 'ana@clinica.local',
  perfil: 'MEDICO' as const,
  clinicaId: 7,
  mustChangePassword: false,
};

const clinic = {
  nome: 'UltraMedical',
  slug: 'ultramedical',
  tipoClinica: 'ULTRASSONOGRAFIA' as const,
  usaCirurgiasNaAgenda: true,
  followUpAutomatico: true,
  n8nWebhookConfigurado: true,
};

describe('MinhaContaClient', () => {
  it('should_render_safe_user_account_summary_without_operational_integrations', () => {
    render(<MinhaContaClient user={user} clinic={clinic} />);

    expect(screen.getByRole('heading', { name: 'Minha conta' })).toBeInTheDocument();
    expect(screen.getByText('Dra. Ana Silva')).toBeInTheDocument();
    expect(screen.getByText('ana@clinica.local')).toBeInTheDocument();
    expect(screen.getByText('Médico')).toBeInTheDocument();
    expect(screen.getByText('UltraMedical')).toBeInTheDocument();
    expect(screen.getByText('Agenda')).toBeInTheDocument();
    expect(screen.getByText('Atendimentos')).toBeInTheDocument();
    expect(screen.queryByText(/Medware/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/N8N/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/WhatsApp/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/secret/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/token/i)).not.toBeInTheDocument();
  });

  it('should_show_administrative_permission_only_for_manager', () => {
    const { rerender } = render(<MinhaContaClient user={user} clinic={clinic} />);

    expect(screen.queryByText('Administração do sistema')).not.toBeInTheDocument();

    rerender(
      <MinhaContaClient
        user={{ ...user, perfil: 'GESTOR' }}
        clinic={clinic}
      />,
    );

    expect(screen.getByText('Administração do sistema')).toBeInTheDocument();
  });
});
