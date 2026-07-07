import { render, screen } from '@testing-library/react';
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
  },
  atendentePrincipal: null,
  tags: [],
};

const baseProps = {
  activeId: null,
  filter: 'TODOS' as const,
  type: 'TODOS' as const,
  search: '',
  onSelect: vi.fn(),
  onFilterChange: vi.fn(),
  onSearchChange: vi.fn(),
};

describe('ChatList', () => {
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
});
