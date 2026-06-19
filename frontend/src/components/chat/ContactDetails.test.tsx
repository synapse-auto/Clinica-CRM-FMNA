import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ContactDetails } from './ContactDetails';
import type { DemoConversation } from '@/mocks/demoAtendimentos';

const conversation: DemoConversation = {
  id: 1,
  name: 'Paciente Teste',
  phone: '44 99999-9999',
  initials: 'PT',
  time: '10:00',
  preview: 'Mensagem',
  tags: ['Consulta'],
  owner: 'Recepção',
  mode: 'Humano',
  status: 'Agendado',
  requerRevisao: false,
};

describe('ContactDetails', () => {
  it('should_hide_confirmation_when_patient_does_not_require_review', () => {
    render(<ContactDetails conversation={conversation} />);

    expect(screen.queryByRole('button', { name: 'Confirmar consulta' })).not.toBeInTheDocument();
  });

  it('should_show_confirmation_only_when_patient_requires_review', () => {
    render(<ContactDetails conversation={{ ...conversation, requerRevisao: true }} />);

    expect(screen.getByRole('button', { name: 'Confirmar consulta' })).toBeInTheDocument();
  });
});
