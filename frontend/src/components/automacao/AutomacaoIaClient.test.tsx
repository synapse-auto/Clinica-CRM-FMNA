import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AutomacaoIaClient } from './AutomacaoIaClient';
import type { ClinicaAtualResponse } from '@/types/dashboard';

const clinic: ClinicaAtualResponse = {
  nome: 'UltraMedical',
  slug: 'ultramedical',
  tipoClinica: 'ULTRASSONOGRAFIA',
  corPrimaria: '#0d9488',
  logoUrl: null,
  usaCirurgiasNaAgenda: false,
  followUpAutomatico: true,
  usaN8n: true,
  n8nWebhookConfigurado: true,
};

describe('AutomacaoIaClient', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('should_enable_real_follow_up_creation_without_read_only_notice', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 30,
      nome: 'Pos exame',
      descricao: 'Mensagem apos exame',
      ativo: true,
      gatilho: 'POS_CONSULTA',
      canal: 'WHATSAPP',
      delayQuantidade: 1,
      delayUnidade: 'DIAS',
      horarioEnvio: '09:00:00',
      mensagemTemplate: 'Como foi seu atendimento?',
    }), {
      status: 201,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);

    render(
      <AutomacaoIaClient
        initialFollowUps={[]}
        initialLembretes={[]}
        initialFestivas={[]}
        initialFila={[]}
        clinic={clinic}
        initialError={null}
      />,
    );

    expect(screen.queryByText(/visualização somente leitura/i)).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /novo follow-up/i }));
    await user.type(screen.getByLabelText('Nome'), 'Pos exame');
    await user.type(screen.getByLabelText('Descrição'), 'Mensagem apos exame');
    await user.type(screen.getByLabelText('Mensagem'), 'Como foi seu atendimento?');
    await user.click(screen.getByRole('button', { name: 'Salvar automação' }));

    await waitFor(() => expect(screen.getByText('Pos exame')).toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledWith('/api/follow-up/config', expect.objectContaining({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        nome: 'Pos exame',
        descricao: 'Mensagem apos exame',
        ativo: true,
        gatilho: 'POS_CONSULTA',
        canal: 'WHATSAPP',
        delayQuantidade: 1,
        delayUnidade: 'DIAS',
        horarioEnvio: '09:00',
        mensagemTemplate: 'Como foi seu atendimento?',
        configJson: null,
      }),
    }));
  });
});
