import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ContactDetails } from './ContactDetails';
import type { AtendimentoDetalhe } from '@/types/atendimento';

const detail: AtendimentoDetalhe = {
  id: 1,
  status: 'ATIVO',
  tratadoPorIa: false,
  dataInicio: '2026-06-19T12:00:00Z',
  dataEncerramento: null,
  naoLidas: 0,
  paciente: {
    id: 10,
    nome: 'Paciente Teste',
    telefone: '44 99999-9999',
    email: null,
    status: 'EM_ATENDIMENTO',
    ultimaInteracaoEm: null,
    requerRevisao: false,
    convenioStatus: null,
    convenioRevisadoEm: null,
    convenioRevisadoPorId: null,
    convenioRevisadoPorNome: null,
  },
  atendentePrincipal: null,
};

const baseProps = {
  atendentes: [],
  canManage: true,
  busy: false,
  onAssume: async () => undefined,
  onTransfer: async () => undefined,
  onReview: async () => undefined,
};

describe('ContactDetails', () => {
  it('should_hide_confirmation_when_patient_does_not_require_review', () => {
    render(<ContactDetails detail={detail} {...baseProps} />);

    expect(screen.queryByText('Verificar convênio')).not.toBeInTheDocument();
  });

  it('should_show_confirmation_only_when_patient_requires_review', () => {
    render(
      <ContactDetails
        detail={{
          ...detail,
          paciente: { ...detail.paciente, requerRevisao: true },
        }}
        {...baseProps}
      />,
    );

    expect(screen.getByText('Verificar convênio')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aprovar' })).toBeInTheDocument();
  });
});
