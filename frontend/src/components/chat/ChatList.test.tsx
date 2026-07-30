import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ChatList } from './ChatList';
import type { AtendimentoResumo } from '@/types/atendimento';

const baseConversation: AtendimentoResumo = {
  id: 1,
  status: 'ATIVO',
  tratadoPorIa: true,
  ultimaMensagemEm: '2026-07-07T12:00:00Z',
  naoLidas: 0,
  ultimaMensagemPrevia: 'Mensagem recente',
  requerRevisao: false,
  convenioStatus: null,
  paciente: {
    id: 10,
    nomeBusca: 'PACIENTE TESTE',
    telefoneNormalizado: '5544999999999',
    fotoUrl: null,
  },
  atendentePrincipal: null,
  tags: [],
};

const baseProps = {
  activeId: null,
  view: 'ATIVOS' as const,
  filter: 'TODOS' as const,
  type: 'TODOS' as const,
  search: '',
  onSelect: vi.fn(),
  onViewChange: vi.fn(),
  onFilterChange: vi.fn(),
  onSearchChange: vi.fn(),
};

describe('ChatList', () => {
  it('should_keep_the_main_view_selector_visible_and_remove_finalized_from_operational_filters', () => {
    const onViewChange = vi.fn();
    render(<ChatList {...baseProps} conversations={[]} onViewChange={onViewChange} />);

    const activeTab = screen.getByRole('tab', { name: 'Em atendimento' });
    const finalizedTab = screen.getByRole('tab', { name: 'Finalizados' });
    expect(activeTab).toHaveAttribute('aria-selected', 'true');
    expect(finalizedTab).toHaveAttribute('aria-selected', 'false');
    expect(screen.getByRole('button', { name: 'Todos' })).toBeInTheDocument();
    fireEvent.click(finalizedTab);
    expect(onViewChange).toHaveBeenCalledWith('FINALIZADOS');
  });

  it('should_render_history_as_read_only_context_without_secondary_filters', () => {
    render(
      <ChatList
        {...baseProps}
        view="FINALIZADOS"
        conversations={[{ ...baseConversation, status: 'ENCERRADO' }]}
      />,
    );

    expect(screen.getByText('Histórico de atendimentos encerrados')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Buscar no histórico...')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Todos' })).not.toBeInTheDocument();
    expect(screen.getByText('Finalizado')).toBeInTheDocument();
    expect(screen.getByText('Encerrado · Atendido por IA')).toBeInTheDocument();
  });

  it('should_show_a_history_specific_empty_state', () => {
    render(<ChatList {...baseProps} view="FINALIZADOS" conversations={[]} />);

    expect(screen.getByText('Nenhum atendimento finalizado.')).toBeInTheDocument();
    expect(screen.getByText('Os atendimentos encerrados aparecerão aqui.')).toBeInTheDocument();
  });

  it('should_show_an_active_specific_empty_state', () => {
    render(<ChatList {...baseProps} conversations={[]} />);

    expect(screen.getByText('Nenhum atendimento ativo encontrado.')).toBeInTheDocument();
    expect(screen.getByText('Ajuste os filtros ou aguarde uma nova conversa.')).toBeInTheDocument();
  });

  it('should_show_manual_start_only_when_the_authenticated_profile_can_use_it', () => {
    const onStartManual = vi.fn();
    const { rerender } = render(
      <ChatList
        {...baseProps}
        conversations={[]}
        canStartManual
        onStartManual={onStartManual}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Novo atendimento' }));
    expect(onStartManual).toHaveBeenCalledOnce();

    rerender(<ChatList {...baseProps} conversations={[]} canStartManual={false} />);
    expect(screen.queryByRole('button', { name: 'Novo atendimento' })).not.toBeInTheDocument();
  });

  it('should_keep_the_title_separate_from_actions_and_make_manual_start_the_full_width_primary_action', () => {
    const onStartManual = vi.fn();
    render(
      <ChatList
        {...baseProps}
        conversations={[]}
        canStartManual
        onStartManual={onStartManual}
      />,
    );

    const title = screen.getByRole('heading', { name: 'Atendimentos' });
    const start = screen.getByRole('button', { name: 'Novo atendimento' });
    expect(title.parentElement?.parentElement).not.toContainElement(start);
    expect(screen.getByText('Ao vivo')).toBeVisible();
    expect(start).toHaveClass('w-full', 'h-10');
  });

  it('should_show_close_all_inside_an_accessible_more_actions_menu_for_managers', () => {
    const onCloseAll = vi.fn();
    render(<ChatList {...baseProps} conversations={[]} canCloseAll onCloseAll={onCloseAll} />);

    const actions = screen.getByRole('button', { name: 'Mais ações dos atendimentos' });
    fireEvent.click(actions);
    const closeAll = screen.getByRole('menuitem', { name: 'Encerrar todos os atendimentos' });
    fireEvent.click(closeAll);

    expect(onCloseAll).toHaveBeenCalledOnce();
    expect(screen.queryByRole('menuitem', { name: 'Encerrar todos os atendimentos' })).not.toBeInTheDocument();
  });

  it('should_close_more_actions_with_escape_and_outside_click_without_losing_search', () => {
    const onSearchChange = vi.fn();
    const { rerender } = render(
      <ChatList
        {...baseProps}
        conversations={[]}
        canCloseAll
        onCloseAll={vi.fn()}
        onSearchChange={onSearchChange}
      />,
    );

    const search = screen.getByRole('searchbox', { name: 'Buscar paciente ou telefone' });
    fireEvent.change(search, { target: { value: 'Maria' } });
    expect(onSearchChange).toHaveBeenCalledWith('Maria');
    rerender(
      <ChatList
        {...baseProps}
        conversations={[]}
        search="Maria"
        canCloseAll
        onCloseAll={vi.fn()}
        onSearchChange={onSearchChange}
      />,
    );
    const actions = screen.getByRole('button', { name: 'Mais ações dos atendimentos' });
    fireEvent.click(actions);
    expect(screen.getByRole('menuitem', { name: 'Encerrar todos os atendimentos' })).toBeInTheDocument();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('menuitem', { name: 'Encerrar todos os atendimentos' })).not.toBeInTheDocument();
    expect(search).toHaveValue('Maria');

    fireEvent.click(actions);
    fireEvent.pointerDown(document.body);
    expect(screen.queryByRole('menuitem', { name: 'Encerrar todos os atendimentos' })).not.toBeInTheDocument();
  });

  it('should_hide_more_actions_when_the_view_or_permission_has_no_available_action', () => {
    const { rerender } = render(<ChatList {...baseProps} conversations={[]} />);
    expect(screen.queryByRole('button', { name: 'Mais ações dos atendimentos' })).not.toBeInTheDocument();

    rerender(<ChatList {...baseProps} conversations={[]} view="FINALIZADOS" canCloseAll onCloseAll={vi.fn()} />);
    expect(screen.queryByRole('button', { name: 'Mais ações dos atendimentos' })).not.toBeInTheDocument();
  });

  it('should_expose_the_more_actions_trigger_for_focus_restoration_after_confirmation', () => {
    const onCloseAllTriggerReady = vi.fn();
    render(
      <ChatList
        {...baseProps}
        conversations={[]}
        canCloseAll
        onCloseAll={vi.fn()}
        onCloseAllTriggerReady={onCloseAllTriggerReady}
      />,
    );

    const restoreFocus = onCloseAllTriggerReady.mock.calls.at(-1)?.[0] as (() => void) | undefined;
    restoreFocus?.();
    expect(screen.getByRole('button', { name: 'Mais ações dos atendimentos' })).toHaveFocus();
  });

  it('should_keep_less_used_operational_filters_in_a_separate_more_filters_menu', () => {
    const onFilterChange = vi.fn();
    render(<ChatList {...baseProps} conversations={[]} onFilterChange={onFilterChange} />);

    expect(screen.queryByRole('button', { name: 'Aguardando' })).not.toBeInTheDocument();
    const moreFilters = screen.getByRole('button', { name: 'Mais filtros de atendimentos' });
    fireEvent.click(moreFilters);
    fireEvent.click(screen.getByRole('menuitem', { name: 'Aguardando' }));
    expect(onFilterChange).toHaveBeenCalledWith('AGUARDANDO', 'TODOS');
  });

  const unicodeName = '𝑨𝒃𝒊𝒎𝒂𝒆𝒍 𝑴𝒐𝒖𝒓𝒂';

  it('should_render_real_tags_and_limit_visual_overflow', () => {
    render(
      <ChatList
        {...baseProps}
        conversations={[
          {
            ...baseConversation,
            tags: [
              { id: 1, nome: 'Retorno', cor: '#0d9488' },
              { id: 2, nome: 'Particular', cor: '#f97316' },
              { id: 3, nome: 'Pre-natal', cor: '#2563eb' },
              { id: 4, nome: 'Urgente', cor: '#dc2626' },
            ],
          },
        ]}
      />,
    );

    expect(screen.getByText('Retorno')).toBeInTheDocument();
    expect(screen.getByText('Particular')).toBeInTheDocument();
    expect(screen.queryByText('Pre-natal')).not.toBeInTheDocument();
    expect(screen.getByText('+2')).toBeInTheDocument();
  });

  it('should_show_ai_and_human_attendance_labels', () => {
    render(
      <ChatList
        {...baseProps}
        conversations={[
          baseConversation,
          {
            ...baseConversation,
            id: 2,
            tratadoPorIa: false,
            paciente: {
              id: 11,
              nomeBusca: 'OUTRA PACIENTE',
              telefoneNormalizado: '5544888888888',
              fotoUrl: null,
            },
            atendentePrincipal: {
              id: 50,
              nome: 'Ana Lima',
            },
          },
        ]}
      />,
    );

    expect(screen.getByText('Atendido por IA')).toBeInTheDocument();
    expect(screen.getByText('Atendido por Ana Lima')).toBeInTheDocument();
  });

  it('should_show_search_progress_inside_the_field_without_adding_a_flow_row', () => {
    render(
      <ChatList
        {...baseProps}
        searching
        conversations={[baseConversation]}
      />,
    );

    const search = screen.getByPlaceholderText('Buscar paciente ou telefone...');
    const label = search.closest('label');
    expect(label).toHaveAttribute('aria-busy', 'true');
    expect(label?.querySelector('.absolute.right-3\\.5')).toBeInTheDocument();
    expect(screen.getByText('Pesquisando atendimentos')).toHaveClass('sr-only');
    expect(screen.queryByText('Pesquisando...')).not.toBeInTheDocument();
  });

  it.each([null, '', '��', 'http://provider.example/avatar'])(
    'should_show_unicode_safe_initials_when_avatar_is_invalid: %s',
    (fotoUrl) => {
      render(
        <ChatList
          {...baseProps}
          conversations={[{
            ...baseConversation,
            paciente: { ...baseConversation.paciente, nomeBusca: unicodeName, fotoUrl },
          }]}
        />,
      );

      expect(screen.getByText('AM')).toBeInTheDocument();
      expect(screen.queryByRole('img', { name: unicodeName })).not.toBeInTheDocument();
    },
  );

  it('should_fallback_after_image_error_and_reset_for_another_contact', () => {
    const { rerender } = render(
      <ChatList
        {...baseProps}
        conversations={[{
          ...baseConversation,
          paciente: {
            ...baseConversation.paciente,
            nomeBusca: unicodeName,
            fotoUrl: 'https://provider.example/avatar/abimael',
          },
        }]}
      />,
    );

    fireEvent.error(screen.getByRole('img', { name: unicodeName }));
    expect(screen.getByText('AM')).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: unicodeName })).not.toBeInTheDocument();

    rerender(
      <ChatList
        {...baseProps}
        conversations={[{
          ...baseConversation,
          paciente: {
            ...baseConversation.paciente,
            nomeBusca: 'BRUNA COSTA',
            fotoUrl: 'https://provider.example/avatar/bruna',
          },
        }]}
      />,
    );

    expect(screen.getByRole('img', { name: 'BRUNA COSTA' })).toHaveAttribute(
      'src',
      'https://provider.example/avatar/bruna',
    );
  });
});
