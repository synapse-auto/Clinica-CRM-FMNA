import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CancelamentosClient } from './CancelamentosClient';
import { apagarTodosCancelamentos, listarCancelamentos } from '@/services/cancelamentos';

vi.mock('@/services/cancelamentos', () => ({
  apagarTodosCancelamentos: vi.fn(),
  listarCancelamentos: vi.fn(),
}));

const initialPage = {
  content: [{
    id: 1,
    pacienteId: 10,
    pacienteNome: 'Maria da Silva',
    telefoneMascarado: '***9999',
    agendamentoId: 20,
    dataHoraAgendamento: '2026-08-25T10:00:00Z',
    profissional: 'Dra. Medware',
    servico: 'Consulta',
    motivo: 'Paciente solicitou',
    origem: 'N8N',
    statusCancelamento: 'CANCELADO',
    statusSincronizacao: 'NAO_APLICAVEL',
    coletadoEm: '2026-08-25T09:00:00Z',
  }],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

beforeEach(() => {
  vi.mocked(listarCancelamentos).mockReset().mockResolvedValue(initialPage);
  vi.mocked(apagarTodosCancelamentos).mockReset().mockResolvedValue(undefined);
});

describe('CancelamentosClient', () => {
  it('should_show_only_domain_filters_and_hide_origin_column', async () => {
    render(<CancelamentosClient canDelete={false} />);

    expect(await screen.findByText('Dra. Medware')).toBeInTheDocument();
    expect(screen.queryByText('Origem')).not.toBeInTheDocument();
    expect(screen.queryByText('Humano')).not.toBeInTheDocument();
    expect(screen.queryByText('Aguardando')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /apagar histórico/i })).not.toBeInTheDocument();
  });

  it('should_confirm_and_reload_after_deleting_all_history', async () => {
    vi.mocked(listarCancelamentos)
      .mockResolvedValueOnce(initialPage)
      .mockResolvedValue({ ...initialPage, content: [], totalElements: 0, totalPages: 0 });
    const user = userEvent.setup();
    render(<CancelamentosClient canDelete />);

    await user.click(await screen.findByRole('button', { name: /apagar histórico/i }));
    expect(screen.getByRole('dialog')).toHaveTextContent('Essa ação não pode ser desfeita.');
    await user.click(screen.getByRole('dialog').querySelector('button.bg-clinic-danger') as HTMLElement);

    await waitFor(() => expect(apagarTodosCancelamentos).toHaveBeenCalledOnce());
    expect(await screen.findByText('Nenhum cancelamento encontrado.')).toBeInTheDocument();
  });
});
