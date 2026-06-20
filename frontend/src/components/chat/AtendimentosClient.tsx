'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import type { AuthUser } from '@/lib/auth/types';
import {
  assumirAtendimento,
  enviarAnexo,
  enviarMensagem,
  getAtendimento,
  getMensagens,
  getNotificacoes,
  getNotificacoesResumo,
  listAtendimentos,
  marcarAtendimentoComoLido,
  marcarNotificacoesComoLidas,
  revisarConvenio,
  transferirAtendimento,
} from '@/services/atendimentos';
import type {
  AtendenteOption,
  AtendimentoDetalhe,
  AtendimentoFilter,
  AtendimentoResumo,
  MensagemAtendimento,
} from '@/types/atendimento';
import { ChatList } from './ChatList';
import { ChatWindow } from './ChatWindow';
import { ContactDetails } from './ContactDetails';

type Props = {
  initialConversations: AtendimentoResumo[];
  atendentes: AtendenteOption[];
  user: AuthUser;
};

export function AtendimentosClient({ initialConversations, atendentes, user }: Props) {
  const [conversations, setConversations] = useState(initialConversations);
  const [activeId, setActiveId] = useState<number | null>(initialConversations[0]?.id ?? null);
  const [detail, setDetail] = useState<AtendimentoDetalhe | null>(null);
  const [messages, setMessages] = useState<MensagemAtendimento[]>([]);
  const [filter, setFilter] = useState<AtendimentoFilter>('TODOS');
  const [type, setType] = useState<'TODOS' | 'IA' | 'HUMANO'>('TODOS');
  const [search, setSearch] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [notificationCount, setNotificationCount] = useState(0);
  const knownNotifications = useRef<Set<number> | null>(null);

  const refreshList = useCallback(async () => {
    try {
      const page = await listAtendimentos({ filtro: filter, tipo: type, busca: search });
      setConversations(page.content);
      setError(null);
      setActiveId((current) => (
        current && page.content.some((item) => item.id === current)
          ? current
          : page.content[0]?.id ?? null
      ));
    } catch (cause) {
      setError(errorMessage(cause));
    }
  }, [filter, search, type]);

  const refreshActive = useCallback(async (id: number) => {
    try {
      const [nextDetail, nextMessages] = await Promise.all([
        getAtendimento(id),
        getMensagens(id),
      ]);
      setDetail(nextDetail);
      setMessages(nextMessages);
      setError(null);
    } catch (cause) {
      setError(errorMessage(cause));
    }
  }, []);

  useEffect(() => {
    const timeout = window.setTimeout(() => void refreshList(), 250);
    return () => window.clearTimeout(timeout);
  }, [refreshList]);

  useEffect(() => {
    if (!activeId) {
      setDetail(null);
      setMessages([]);
      return;
    }
    void marcarAtendimentoComoLido(activeId)
      .then(() => Promise.all([refreshActive(activeId), refreshList()]))
      .catch((cause) => setError(errorMessage(cause)));
  }, [activeId, refreshActive, refreshList]);

  useEffect(() => {
    const interval = window.setInterval(() => {
      void refreshList();
      if (activeId) void refreshActive(activeId);
    }, 5000);
    return () => window.clearInterval(interval);
  }, [activeId, refreshActive, refreshList]);

  useEffect(() => {
    async function pollNotifications() {
      try {
        const [items, count] = await Promise.all([
          getNotificacoes(),
          getNotificacoesResumo(),
        ]);
        const ids = new Set(items.map((item) => item.id));
        if (knownNotifications.current) {
          const hasNew = items.some((item) => !knownNotifications.current?.has(item.id));
          if (hasNew) setNotificationCount(count);
        } else {
          setNotificationCount(count);
        }
        knownNotifications.current = ids;
        window.dispatchEvent(new CustomEvent('atendimentos:badge', { detail: count }));
      } catch {
        // O erro principal da tela continua reservado às operações do atendimento.
      }
    }
    void pollNotifications();
    const interval = window.setInterval(() => void pollNotifications(), 10000);
    return () => window.clearInterval(interval);
  }, []);

  async function runAction(action: () => Promise<unknown>) {
    setBusy(true);
    try {
      await action();
      if (activeId) await refreshActive(activeId);
      await refreshList();
      setError(null);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function dismissNotifications() {
    try {
      await marcarNotificacoesComoLidas();
      setNotificationCount(0);
      window.dispatchEvent(new CustomEvent('atendimentos:badge', { detail: 0 }));
    } catch (cause) {
      setError(errorMessage(cause));
    }
  }

  return (
    <div className="relative flex h-full overflow-hidden bg-clinic-canvas">
      {notificationCount > 0 ? (
        <div className="absolute right-4 top-3 z-30 flex items-center gap-3 rounded-lg border border-clinic-primary/30 bg-clinic-surface px-3 py-2 text-[11px] font-semibold text-clinic-text shadow-lg">
          {notificationCount} notificação(ões) nova(s)
          <button className="text-clinic-primary" onClick={() => void dismissNotifications()}>
            Marcar como lidas
          </button>
        </div>
      ) : null}

      <ChatList
        conversations={conversations}
        activeId={activeId}
        filter={filter}
        type={type}
        search={search}
        onSelect={setActiveId}
        onFilterChange={(nextFilter, nextType) => {
          setFilter(nextFilter);
          setType(nextType);
        }}
        onSearchChange={setSearch}
      />
      <ChatWindow
        detail={detail}
        messages={messages}
        busy={busy}
        error={error}
        onSend={(content) => activeId
          ? runAction(() => enviarMensagem(activeId, content))
          : Promise.resolve()}
        onAttach={(file) => activeId
          ? runAction(() => enviarAnexo(activeId, file))
          : Promise.resolve()}
      />
      <ContactDetails
        detail={detail}
        atendentes={atendentes}
        canManage={user.perfil !== 'MEDICO'}
        busy={busy}
        onAssume={() => activeId
          ? runAction(() => assumirAtendimento(activeId))
          : Promise.resolve()}
        onTransfer={(usuarioId) => activeId
          ? runAction(() => transferirAtendimento(activeId, usuarioId))
          : Promise.resolve()}
        onReview={(result) => activeId
          ? runAction(() => revisarConvenio(activeId, result))
          : Promise.resolve()}
      />
    </div>
  );
}

function errorMessage(cause: unknown) {
  return cause instanceof Error ? cause.message : 'Não foi possível concluir a operação';
}
