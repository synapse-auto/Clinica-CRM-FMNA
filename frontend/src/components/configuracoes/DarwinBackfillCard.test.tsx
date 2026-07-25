import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DarwinBackfillCard } from './DarwinBackfillCard';
import type { DarwinBackfillStatus, DarwinStatus } from '@/types/darwin';

const services = vi.hoisted(() => ({
  getDarwinStatus: vi.fn(),
  getBackfillStatus: vi.fn(),
  iniciarBackfill: vi.fn(),
  cancelarBackfill: vi.fn(),
}));

vi.mock('@/services/darwinBackfill', () => services);

const darwinStatus: DarwinStatus = {
  enabled: true,
  provider: 'DARWIN',
  configured: true,
  bulkSyncSupported: false,
  onDemandQueriesSupported: true,
  clinicWideListingSupported: false,
  localMirrorEnabled: true,
  knownPatientsBackfillSupported: true,
  coverage: 'KNOWN_CRM_PATIENTS_ONLY',
};

const idleStatus: DarwinBackfillStatus = {
  status: 'IDLE',
  totalPacientes: 0,
  processados: 0,
  comErro: 0,
  iniciadoEm: null,
  finalizadoEm: null,
};

describe('DarwinBackfillCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_render_nothing_for_medware_clinics', async () => {
    services.getDarwinStatus.mockResolvedValue({ ...darwinStatus, provider: 'MEDWARE' });
    services.getBackfillStatus.mockResolvedValue(idleStatus);

    const { container } = render(<DarwinBackfillCard />);

    await waitFor(() => expect(services.getDarwinStatus).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('should_show_permission_notice_when_backfill_status_is_forbidden', async () => {
    services.getDarwinStatus.mockResolvedValue(darwinStatus);
    services.getBackfillStatus.mockRejectedValue(new Error('Acesso negado.'));

    render(<DarwinBackfillCard />);

    expect(await screen.findByText(
      'Apenas usuários administrativos internos podem iniciar ou acompanhar o backfill Darwin.',
    )).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /iniciar backfill/i })).not.toBeInTheDocument();
  });

  it('should_require_confirmation_before_starting_backfill', async () => {
    const user = userEvent.setup();
    services.getDarwinStatus.mockResolvedValue(darwinStatus);
    services.getBackfillStatus.mockResolvedValue(idleStatus);
    services.iniciarBackfill.mockResolvedValue({ ...idleStatus, status: 'RUNNING', totalPacientes: 10 });

    render(<DarwinBackfillCard />);

    const startButton = await screen.findByRole('button', { name: /iniciar backfill/i });
    expect(services.iniciarBackfill).not.toHaveBeenCalled();

    await user.click(startButton);
    expect(screen.getByText('Confirma iniciar o backfill agora?')).toBeInTheDocument();
    expect(services.iniciarBackfill).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: /confirmar início/i }));

    await waitFor(() => expect(services.iniciarBackfill).toHaveBeenCalledTimes(1));
    expect(await screen.findByText('RUNNING')).toBeInTheDocument();
  });

  it('should_show_coverage_warning_and_allow_cancelling_a_running_backfill', async () => {
    const user = userEvent.setup();
    services.getDarwinStatus.mockResolvedValue(darwinStatus);
    services.getBackfillStatus.mockResolvedValue({
      status: 'RUNNING', totalPacientes: 10, processados: 4, comErro: 1, iniciadoEm: '2026-07-20T10:00:00Z', finalizadoEm: null,
    });
    services.cancelarBackfill.mockResolvedValue({
      status: 'CANCELADO', totalPacientes: 10, processados: 4, comErro: 1, iniciadoEm: '2026-07-20T10:00:00Z', finalizadoEm: '2026-07-20T10:05:00Z',
    });

    render(<DarwinBackfillCard />);

    expect(await screen.findByText(/KNOWN_CRM_PATIENTS_ONLY/)).toBeInTheDocument();
    expect(screen.getByText(/perdido se o backend reiniciar/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /cancelar backfill/i }));

    await waitFor(() => expect(services.cancelarBackfill).toHaveBeenCalledTimes(1));
    expect(await screen.findByText('CANCELADO')).toBeInTheDocument();
  });
});
