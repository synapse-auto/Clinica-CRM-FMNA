import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ContactAvatar } from './ContactAvatar';

describe('ContactAvatar — iniciais centralizadas', () => {
  it('should_render_two_initials_for_a_two_word_name', () => {
    render(<ContactAvatar name="Maria Silva" />);
    expect(screen.getByText('MS')).toBeInTheDocument();
  });

  it('should_render_two_letters_for_a_single_word_name', () => {
    render(<ContactAvatar name="Maria" />);
    expect(screen.getByText('MA')).toBeInTheDocument();
  });

  it('should_fallback_to_wa_when_name_is_only_a_phone_number', () => {
    render(<ContactAvatar name="5511999990000" />);
    expect(screen.getByText('WA')).toBeInTheDocument();
  });

  it('should_fallback_to_wa_when_name_is_a_formatted_phone_number', () => {
    render(<ContactAvatar name="+55 (11) 99999-0000" />);
    expect(screen.getByText('WA')).toBeInTheDocument();
  });

  it('should_fallback_to_wa_when_name_is_empty', () => {
    render(<ContactAvatar name="" />);
    expect(screen.getByText('WA')).toBeInTheDocument();
  });

  it('should_fallback_to_wa_when_name_is_blank', () => {
    render(<ContactAvatar name="   " />);
    expect(screen.getByText('WA')).toBeInTheDocument();
  });

  it('should_fallback_to_wa_for_the_generic_whatsapp_contact_placeholder', () => {
    render(<ContactAvatar name="Contato WhatsApp" />);
    expect(screen.getByText('WA')).toBeInTheDocument();
  });

  it('should_fallback_to_wa_for_the_literal_null_string', () => {
    render(<ContactAvatar name="null" />);
    expect(screen.getByText('WA')).toBeInTheDocument();
  });

  it('should_never_render_an_empty_initials_string', () => {
    render(<ContactAvatar name="123" />);
    const avatar = screen.getByText((content) => content.length > 0);
    expect(avatar.textContent).not.toBe('');
  });

  it('should_show_the_image_when_a_valid_https_photo_url_is_provided', () => {
    render(<ContactAvatar name="Maria Silva" url="https://cdn.example.com/foto.jpg" />);
    expect(screen.getByRole('img')).toBeInTheDocument();
    expect(screen.queryByText('MS')).not.toBeInTheDocument();
  });

  it('should_fallback_to_initials_automatically_when_the_image_fails_to_load', () => {
    render(<ContactAvatar name="Maria Silva" url="https://cdn.example.com/foto-quebrada.jpg" />);
    const img = screen.getByRole('img');

    fireEvent.error(img);

    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getByText('MS')).toBeInTheDocument();
  });

  it('should_render_initials_with_contrast_classes_for_visible_text', () => {
    render(<ContactAvatar name="Maria Silva" />);
    const initialsSpan = screen.getByText('MS');
    expect(initialsSpan.parentElement).toHaveClass('text-clinic-primary');
    expect(initialsSpan.parentElement).toHaveClass('bg-clinic-primary/15');
  });
});
