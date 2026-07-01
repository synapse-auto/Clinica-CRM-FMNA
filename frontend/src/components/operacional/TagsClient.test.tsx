import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { TagsClient } from './TagsClient';
import type { TagOperacional } from '@/types/operacional';

const tags: TagOperacional[] = [
  {
    id: 3,
    nome: 'Prioridade real',
    cor: '#ef4444',
    descricao: 'Pacientes prioritarias',
    ativo: true,
    criadoEm: '2026-07-01T12:00:00Z',
    atualizadoEm: '2026-07-01T12:00:00Z',
  },
];

describe('TagsClient', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('should_render_real_tags_without_fake_operational_tags', () => {
    render(<TagsClient initialTags={tags} initialError={null} canManage />);

    expect(screen.getByText('Prioridade real')).toBeInTheDocument();
    expect(screen.queryByText('Plano de Saúde')).not.toBeInTheDocument();
  });

  it('should_create_tag_using_bff', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      ...tags[0],
      id: 4,
      nome: 'Ultrassom',
      cor: '#2563eb',
      descricao: 'Interesse em exame',
    }), {
      status: 201,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);
    render(<TagsClient initialTags={[]} initialError={null} canManage />);

    await user.click(screen.getByRole('button', { name: /nova tag/i }));
    await user.type(screen.getByLabelText('Nome'), 'Ultrassom');
    await user.click(screen.getByRole('button', { name: 'Usar cor #2563eb' }));
    expect(screen.getByLabelText('Hex da cor')).toHaveValue('#2563eb');
    await user.type(screen.getByLabelText('Descrição'), 'Interesse em exame');
    await user.click(screen.getByRole('button', { name: 'Salvar tag' }));

    await waitFor(() => expect(screen.getByText('Ultrassom')).toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledWith('/api/tags', expect.objectContaining({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        nome: 'Ultrassom',
        cor: '#2563eb',
        descricao: 'Interesse em exame',
        ativo: true,
      }),
    }));
  });
});
