import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ContactDetails } from './ContactDetails';
import type { AtendimentoDetalhe, AtendimentoLembrete } from '@/types/atendimento';
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
    fotoUrl: null,
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
  onActivateIa: async () => undefined,
  onTransfer: async () => undefined,
  onReview: async () => undefined,
  onAddTag: async () => undefined,
  onRemoveTag: async () => undefined,
  reminders: [],
  remindersLoading: false,
  remindersError: null,
  onCreateReminder: async () => undefined,
  onConcludeReminder: async () => undefined,
  onCancelReminder: async () => undefined,
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

const reminder: AtendimentoLembrete = {
  id: 20,
  atendimentoId: 1,
  mensagem: 'Conferir autorizacao do plano',
  lembrarEm: '2026-07-10T10:00:00Z',
  status: 'PENDENTE',
  criadoPorId: 3,
  criadoPorNome: 'Recepcao',
  criadoEm: '2026-07-01T10:00:00Z',
  atualizadoEm: '2026-07-01T10:00:00Z',
};

describe('ContactDetails', () => {
  it('should_show_unicode_safe_initials_in_patient_details', () => {
    render(
      <ContactDetails
        detail={{
          ...detail,
          paciente: { ...detail.paciente, nome: '𝑨𝒃𝒊𝒎𝒂𝒆𝒍 𝑴𝒐𝒖𝒓𝒂', fotoUrl: null },
        }}
        {...baseProps}
      />,
    );

    expect(screen.getByText('AM')).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: '𝑨𝒃𝒊𝒎𝒂𝒆𝒍 𝑴𝒐𝒖𝒓𝒂' })).not.toBeInTheDocument();
  });

  it('should_show_initials_when_patient_image_fails', () => {
    render(
      <ContactDetails
        detail={{
          ...detail,
          paciente: {
            ...detail.paciente,
            nome: 'Abimael Moura',
            fotoUrl: 'https://provider.example/avatar/abimael',
          },
        }}
        {...baseProps}
      />,
    );

    fireEvent.error(screen.getByRole('img', { name: 'Abimael Moura' }));

    expect(screen.getByText('AM')).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: 'Abimael Moura' })).not.toBeInTheDocument();
  });

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
    await user.click(screen.getByLabelText('Selecionar tag para adicionar'));
    await user.click(await screen.findByRole('option', { name: 'Retorno' }));
    await user.click(screen.getByRole('button', { name: 'Remover tag Prioridade' }));

    expect(onAddTag).toHaveBeenCalledWith(4);
    expect(onRemoveTag).toHaveBeenCalledWith(3);
  });

  it('should_allow_returning_human_atendimento_to_ai', async () => {
    const user = userEvent.setup();
    const onActivateIa = vi.fn();

    render(
      <ContactDetails
        detail={detail}
        {...baseProps}
        onActivateIa={onActivateIa}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Voltar para IA' }));

    expect(onActivateIa).toHaveBeenCalledTimes(1);
  });

  it('should_hide_return_to_ai_action_when_atendimento_is_already_ai', () => {
    render(
      <ContactDetails
        detail={{ ...detail, tratadoPorIa: true }}
        {...baseProps}
      />,
    );

    expect(screen.queryByRole('button', { name: 'Voltar para IA' })).not.toBeInTheDocument();
  });

  it('should_show_empty_internal_reminders_section', () => {
    render(<ContactDetails detail={detail} {...baseProps} />);

    expect(screen.getByText('Lembretes')).toBeInTheDocument();
    expect(screen.getByText('Nenhum lembrete para este atendimento.')).toBeInTheDocument();
  });

  it('should_create_conclude_and_cancel_internal_reminders', async () => {
    const user = userEvent.setup();
    const onCreateReminder = vi.fn();
    const onConcludeReminder = vi.fn();
    const onCancelReminder = vi.fn();

    render(
      <ContactDetails
        detail={detail}
        {...baseProps}
        reminders={[reminder]}
        onCreateReminder={onCreateReminder}
        onConcludeReminder={onConcludeReminder}
        onCancelReminder={onCancelReminder}
      />,
    );

    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-07-01T12:00:00-03:00'));
    await user.click(screen.getByLabelText('Data do lembrete'));
    await user.click(await screen.findByRole('button', { name: /12 de julho/i }));
    await user.type(screen.getByLabelText('Hora do lembrete'), '14:30');
    await user.type(screen.getByLabelText('Mensagem do lembrete'), 'Retornar com horarios disponiveis');
    await user.click(screen.getByRole('button', { name: 'Adicionar lembrete' }));
    await user.click(screen.getByRole('button', { name: 'Concluir lembrete' }));
    await user.click(screen.getByRole('button', { name: 'Cancelar lembrete' }));

    expect(onCreateReminder).toHaveBeenCalledWith({
      data: '2026-07-12',
      hora: '14:30',
      mensagem: 'Retornar com horarios disponiveis',
    });
    expect(onConcludeReminder).toHaveBeenCalledWith(20);
    expect(onCancelReminder).toHaveBeenCalledWith(20);
    vi.useRealTimers();
  });
});
