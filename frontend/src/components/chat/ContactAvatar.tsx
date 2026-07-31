'use client';

import { useState } from 'react';

type Props = {
  name: string;
  url?: string | null;
  variant?: 'list' | 'details' | 'sidebar';
};

export function ContactAvatar({ name, url, variant = 'list' }: Props) {
  const safeUrl = validAvatarUrl(url);
  const imageKey = safeUrl ? `${name}\u0000${safeUrl}` : null;
  const [failedImageKey, setFailedImageKey] = useState<string | null>(null);
  const showImage = imageKey !== null && imageKey !== failedImageKey;
  const details = variant === 'details';
  const sidebar = variant === 'sidebar';

  return (
    <div
      className={details
        ? 'relative mx-auto mb-3 flex h-[72px] w-[72px] shrink-0 items-center justify-center overflow-hidden rounded-full bg-clinic-primary/15 text-xl font-extrabold text-clinic-primary ring-4 ring-clinic-soft'
        : sidebar
          ? 'relative flex h-11 w-11 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-clinic-primary/15 text-sm font-extrabold text-clinic-primary'
          : 'relative flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-full bg-clinic-primary/15 text-sm font-extrabold text-clinic-primary'}
    >
      {showImage && safeUrl ? (
        <img
          src={safeUrl}
          alt={avatarAlt(name)}
          loading="lazy"
          decoding="async"
          className="absolute inset-0 h-full w-full object-cover"
          onError={() => setFailedImageKey(imageKey)}
        />
      ) : (
        <span>{initials(name)}</span>
      )}
    </div>
  );
}

// Placeholders conhecidos: sem nome real cadastrado (ver WhatsappInboundMapper no backend).
const NOME_PLACEHOLDER = new Set(['contato whatsapp', 'null']);
// Só dígitos e pontuação comum de telefone (sem nenhuma letra) — não é um nome real.
const TELEFONE_REGEX = /^[+\d\s().-]+$/;

/**
 * Gera as iniciais exibidas no avatar. Nunca retorna string vazia: quando não há nome real
 * (placeholder, telefone puro, vazio/nulo), cai em "WA".
 */
function initials(name: string) {
  const cleaned = name.normalize('NFKD').trim();
  if (!cleaned || NOME_PLACEHOLDER.has(cleaned.toLowerCase()) || TELEFONE_REGEX.test(cleaned)) {
    return 'WA';
  }

  const palavras = cleaned.split(/\s+/).filter(Boolean);
  if (palavras.length >= 2) {
    const duasIniciais = palavras
      .slice(0, 2)
      .map((palavra) => Array.from(palavra)[0]?.toUpperCase() ?? '')
      .join('');
    return duasIniciais || 'WA';
  }

  // Nome de uma única palavra: usa até as duas primeiras letras (não apenas a primeira).
  const letras = Array.from(palavras[0] ?? '').filter((caractere) => /\p{L}/u.test(caractere));
  if (letras.length === 0) {
    return 'WA';
  }
  return letras.slice(0, 2).join('').toUpperCase();
}

const INTERNAL_PATIENT_PHOTO =
  /^\/api\/pacientes\/\d+\/foto(?:\?v=[a-f0-9]{8,64})?$/i;

function validAvatarUrl(value?: string | null) {
  if (typeof value !== 'string' || !value.trim()) return null;
  const trimmed = value.trim();
  if (INTERNAL_PATIENT_PHOTO.test(trimmed)) return trimmed;
  try {
    const url = new URL(trimmed);
    return url.protocol === 'https:' && !url.username && !url.password ? url.toString() : null;
  } catch {
    return null;
  }
}

function avatarAlt(name: string) {
  const cleaned = name.normalize('NFKD').trim();
  return cleaned && !TELEFONE_REGEX.test(cleaned) && !NOME_PLACEHOLDER.has(cleaned.toLowerCase())
    ? name
    : 'Foto do contato';
}
