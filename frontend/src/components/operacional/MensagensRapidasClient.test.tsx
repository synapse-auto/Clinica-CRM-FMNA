import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MensagensRapidasClient } from './MensagensRapidasClient';
import type { CategoriaMensagemRapida, MensagemRapida } from '@/types/operacional';

const categories: CategoriaMensagemRapida[] = [
  { id: 1, codigo: 'AGENDAMENTO', rotulo: 'Agendamento', cor: '#0d9488' },
];

const messages: MensagemRapida[] = [
  {
    id: 10,
    categoriaId: 1,
    categoriaRotulo: 'Agendamento',
    categoriaCor: '#0d9488',
    titulo: 'Confirmacao real',
    atalho: '/confirmar',
    conteudo: 'Sua consulta esta confirmada.',
    ativo: true,
    criadoEm: '2026-07-01T12:00:00Z',
    atualizadoEm: '2026-07-01T12:00:00Z',
  },
];

describe('MensagensRapidasClient', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('should_render_real_quick_messages_without_fake_templates', () => {
    render(<MensagensRapidasClient initialMessages={messages} categories={categories} initialError={null} canManage />);

    expect(screen.getByText('Confirmacao real')).toBeInTheDocument();
    expect(screen.getByText('/confirmar')).toBeInTheDocument();
    expect(screen.queryByText('Boas-vindas')).not.toBeInTheDocument();
  });

  it('should_create_quick_message_using_bff', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      ...messages[0],
      id: 22,
      titulo: 'Preparo exame',
      atalho: '/preparo',
      conteudo: 'Chegue com 15 minutos de antecedencia.',
    }), {
      status: 201,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);
    render(<MensagensRapidasClient initialMessages={[]} categories={categories} initialError={null} canManage />);

    await user.click(screen.getByRole('button', { name: /nova mensagem/i }));
    await user.type(screen.getByLabelText('Título'), 'Preparo exame');
    await user.type(screen.getByLabelText('Atalho'), '/preparo');
    await user.selectOptions(screen.getByLabelText('Categoria'), '1');
    await user.type(screen.getByLabelText('Conteúdo'), 'Chegue com 15 minutos de antecedencia.');
    await user.click(screen.getByRole('button', { name: 'Salvar mensagem' }));

    await waitFor(() => expect(screen.getByText('Preparo exame')).toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledWith('/api/mensagens-rapidas', expect.objectContaining({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        categoriaId: 1,
        titulo: 'Preparo exame',
        atalho: '/preparo',
        conteudo: 'Chegue com 15 minutos de antecedencia.',
        ativo: true,
      }),
    }));
  });
});
