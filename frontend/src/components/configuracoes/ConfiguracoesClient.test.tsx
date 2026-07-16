import { render, screen } from '@testing-library/react';
import { ConfiguracoesClient } from './ConfiguracoesClient';
import type { ConfiguracaoResumo } from '@/types/configuracoes';

const resumo: ConfiguracaoResumo = {
  identidade: {
    nome: 'UltraMedical',
    slug: 'ultramedical',
    tipoClinica: 'ULTRASSONOGRAFIA',
    externalProvider: 'MEDWARE',
    statusOperacional: 'Operacional',
    whatsappConfigurado: true,
    n8nConfigurado: true,
  },
  integracoes: [
    { nome: 'WhatsApp Oficial', status: 'Configurado', detalhe: 'Webhook oficial ativo' },
    { nome: 'N8N', status: 'Configurado', detalhe: 'Callback protegido por segredo' },
    { nome: 'Medware', status: 'Pendente', detalhe: 'Ultima sync falhou' },
  ],
  ultimaSincronizacaoMedware: {
    status: 'FALHA_TOTAL',
    iniciadoEm: '2026-07-06T15:00:00Z',
    concluidoEm: '2026-07-06T15:01:00Z',
    dataInicio: '01/06/2026',
    dataFim: '31/07/2026',
    pacientesProcessados: 0,
    agendamentosProcessados: 0,
    agendamentosIgnorados: 0,
    erroResumo: 'MEDWARE_API_URL invalida',
  },
  seguranca: {
    perfisAtivos: [
      { perfil: 'GESTOR', total: 2 },
      { perfil: 'RECEPCIONISTA', total: 5 },
    ],
    regras: ['Sessao protegida por JWT', 'Logs sem dados sensiveis'],
  },
  operacao: {
    horariosConfigurados: true,
    iaAtiva: true,
    retornoHumanoIa24h: true,
    agendaMedicoSomenteLeitura: true,
    mutacaoAgendaRestrita: true,
  },
  ambiente: {
    nome: 'teste',
    inicializadoEm: '2026-07-06T14:55:00Z',
  },
};

describe('ConfiguracoesClient', () => {
  it('should_render_operational_configuration_summary_without_sensitive_values', () => {
    render(<ConfiguracoesClient resumo={resumo} />);

    expect(screen.getByText('UltraMedical')).toBeInTheDocument();
    expect(screen.getByText('MEDWARE')).toBeInTheDocument();
    expect(screen.getByText('WhatsApp Oficial')).toBeInTheDocument();
    expect(screen.getByText('Automação')).toBeInTheDocument();
    expect(screen.queryByText('N8N')).not.toBeInTheDocument();
    expect(screen.getByText('FALHA_TOTAL')).toBeInTheDocument();
    expect(screen.getByText('Retorno HUMANO -> IA em 24h')).toBeInTheDocument();
    expect(screen.queryByText(/https:\/\/n8n/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/secret/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /salvar|sincronizar|editar/i })).not.toBeInTheDocument();
  });
});
