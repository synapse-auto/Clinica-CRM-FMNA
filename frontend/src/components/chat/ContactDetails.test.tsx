import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ContactDetails } from './ContactDetails';
import type { AtendimentoDetalhe } from '@/types/atendimento';
import type { TagOperacional } from '@/types/operacional';

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
  tags: [],
  availableTags: [],
  canManage: true,
  busy: false,
  onAssume: async () => undefined,
  onTransfer: async () => undefined,
  onReview: async () => undefined,
  onAddTag: async () => undefined,
  onRemoveTag: async () => undefined,
};

const tags: TagOperacional[] = [
  {
    id: 3,
    nome: 'Prioridade',
    cor: '#ef4444',
    descricao: null,
    ativo: true,
    criadoEm: null,
    atualizadoEm: null,
  },
  {
    id: 4,
    nome: 'Retorno',
    cor: '#0d9488',
    descricao: null,
    ativo: true,
    criadoEm: null,
    atualizadoEm: null,
  },
];

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

  it('should_add_and_remove_real_tags_from_atendimento', async () => {
    const user = userEvent.setup();
    const onAddTag = vi.fn();
    const onRemoveTag = vi.fn();

    render(
      <ContactDetails
        detail={detail}
        {...baseProps}
        tags={[tags[0]]}
        availableTags={tags}
        onAddTag={onAddTag}
        onRemoveTag={onRemoveTag}
      />,
    );

    expect(screen.getByText('Prioridade')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Adicionar tag' }));
    await user.selectOptions(screen.getByLabelText('Selecionar tag para adicionar'), '4');
    await user.click(screen.getByRole('button', { name: 'Remover tag Prioridade' }));

    expect(onAddTag).toHaveBeenCalledWith(4);
    expect(onRemoveTag).toHaveBeenCalledWith(3);
  });
});
