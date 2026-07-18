'use client';

import {
  Activity,
  Mail,
  Phone,
  Plus,
  Search,
  Tag,
  Trash2,
  Users,
  X,
} from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { LucideIcon } from 'lucide-react';
import { DemoTable } from '@/components/demo/DemoTable';
import { StatusBadge } from '@/components/demo/StatusBadge';
import {
  adicionarTagPaciente,
  pesquisarPacientes,
  removerTagPaciente,
} from '@/services/pacientes';
import type { TagOperacional } from '@/types/operacional';
import type { PacientePage, PacienteResumo } from '@/types/paciente';
import { isSearchableTerm, matchesSearchTokens, normalizeSearchText } from '@/lib/search';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';

type Props = {
  initialPage: PacientePage;
  availableTags: TagOperacional[];
  initialError: string | null;
  canManage: boolean;
};

export function PacientesClient({
  initialPage,
  availableTags,
  initialError,
  canManage,
}: Props) {
  const [pacientes, setPacientes] = useState(initialPage.content);
  const [page, setPage] = useState(initialPage.number);
  const [totalElements, setTotalElements] = useState(initialPage.totalElements);
  const [totalPages, setTotalPages] = useState(initialPage.totalPages);
  const [counts, setCounts] = useState(initialPage.counts);
  const [selectedPatient, setSelectedPatient] = useState<PacienteResumo | null>(null);
  const [tagSearch, setTagSearch] = useState('');
  const [patientSearch, setPatientSearch] = useState('');
  const [searching, setSearching] = useState(false);
  const [retryVersion, setRetryVersion] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(initialError);
  const debouncedSearch = useDebouncedValue(patientSearch, 300);
  const normalizedSearch = normalizeSearchText(debouncedSearch);
  const searchKey = isSearchableTerm(debouncedSearch) ? normalizedSearch : '';
  const query = searchKey ? debouncedSearch.trim() : '';
  const queryRef = useRef(query);
  queryRef.current = query;
  const initialRequest = useRef(true);
  const requestVersion = useRef(0);
  const activeTags = useMemo(
    () => availableTags
      .filter((tagItem) => tagItem.ativo)
      .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')),
    [availableTags],
  );
  const selectedTags = selectedPatient?.tags ?? [];
  const selectedTagIds = new Set(selectedTags.map((tagItem) => tagItem.id));
  const tagsToAdd = activeTags
    .filter((tagItem) => !selectedTagIds.has(tagItem.id))
    .filter((tagItem) => matchesSearchTokens([tagItem.nome], tagSearch));

  useEffect(() => {
    if (initialRequest.current) {
      initialRequest.current = false;
      return;
    }
    const controller = new AbortController();
    const currentRequest = ++requestVersion.current;
    setSearching(true);
    pesquisarPacientes({ q: queryRef.current, page, size: 25 }, controller.signal)
      .then((response) => {
        if (controller.signal.aborted || currentRequest !== requestVersion.current) return;
        setPacientes(response.content);
        setTotalElements(response.totalElements);
        setTotalPages(response.totalPages);
        setCounts(response.counts);
        setError(null);
      })
      .catch((cause) => {
        if (controller.signal.aborted || currentRequest !== requestVersion.current) return;
        setError(errorMessage(cause));
      })
      .finally(() => {
        if (!controller.signal.aborted && currentRequest === requestVersion.current) {
          setSearching(false);
        }
      });
    return () => controller.abort();
  }, [searchKey, page, retryVersion]);

  function changeSearch(value: string) {
    setPatientSearch(value);
    setPage(0);
  }

  async function addTag(tagId: number) {
    if (!selectedPatient) return;
    setBusy(true);
    try {
      const nextTags = await adicionarTagPaciente(selectedPatient.id, tagId);
      updatePatientTags(selectedPatient.id, nextTags);
      setTagSearch('');
      setSelectedPatient(null);
      setError(null);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function removeTag(patient: PacienteResumo, tagId: number) {
    setBusy(true);
    try {
      await removerTagPaciente(patient.id, tagId);
      const nextTags = (patient.tags ?? []).filter((tagItem) => tagItem.id !== tagId);
      updatePatientTags(patient.id, nextTags);
      setSelectedPatient((current) => (
        current?.id === patient.id ? { ...current, tags: nextTags } : current
      ));
      setError(null);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  function updatePatientTags(patientId: number, tags: TagOperacional[]) {
    setPacientes((current) => current.map((paciente) => (
      paciente.id === patientId ? { ...paciente, tags } : paciente
    )));
  }

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <header className="mb-3 border-b border-clinic-border pb-3">
        <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
          <div>
            <h1 className="text-[17px] font-extrabold leading-6 text-clinic-text">Pacientes</h1>
            <p className="text-[10px] text-clinic-muted">{counts.total} pacientes</p>
          </div>
        </div>
      </header>

      {error ? (
        <div role="alert" className="mb-3 flex items-center justify-between gap-3 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[10px] font-semibold text-clinic-danger">
          <span>{error}</span>
          <button
            type="button"
            onClick={() => setRetryVersion((current) => current + 1)}
            className="shrink-0 font-extrabold underline"
          >
            Tentar novamente
          </button>
        </div>
      ) : null}

      <div className="mb-3 grid grid-cols-1 gap-3 xl:grid-cols-[minmax(0,1fr)_180px]">
        <section className="flex min-h-[64px] flex-wrap items-center gap-3 rounded-xl border border-clinic-border bg-clinic-surface px-4 py-3 shadow-[0_1px_2px_rgba(4,32,36,0.04)]">
          <div className="flex items-center gap-2 border-r border-clinic-border pr-3 text-[10px] font-bold text-clinic-text">
            <Activity className="h-4 w-4 text-clinic-primary" />
            Status Atual
          </div>
          <StatusBadge tone="green">{`${counts.emAtendimento} Em Atendimento`}</StatusBadge>
          <StatusBadge tone="blue">{`${counts.agendado} Agendados`}</StatusBadge>
          <StatusBadge tone="orange">{`${counts.outros} Outros`}</StatusBadge>
          <StatusBadge tone="slate">{`${counts.finalizado} Finalizados`}</StatusBadge>
        </section>

        <SummaryCard
          icon={Users}
          value={counts.total.toString()}
          label="Total de Pacientes"
        />
      </div>

      {counts.total > 0 ? (
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <label className="flex h-9 min-w-0 flex-1 items-center gap-2 rounded-lg border border-clinic-border bg-clinic-input px-2 text-clinic-muted focus-within:ring-2 focus-within:ring-clinic-primary/35 sm:max-w-md">
            <Search className="h-3.5 w-3.5 shrink-0" />
            <input
              type="search"
              value={patientSearch}
              onChange={(event) => changeSearch(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Escape') changeSearch('');
              }}
              aria-label="Buscar pacientes"
              placeholder="Buscar por nome, telefone ou ID..."
              className="min-w-0 flex-1 bg-transparent text-[11px] font-semibold text-clinic-text outline-none placeholder:text-clinic-muted"
            />
            {patientSearch ? (
              <button type="button" aria-label="Limpar pesquisa de pacientes" onClick={() => changeSearch('')} className="rounded p-0.5 hover:bg-clinic-hover hover:text-clinic-text">
                <X className="h-3.5 w-3.5" />
              </button>
            ) : null}
          </label>
          <p className="text-[10px] font-semibold text-clinic-muted">
            {searching ? 'Pesquisando...' : `${pacientes.length} de ${totalElements} resultados`}
          </p>
        </div>
      ) : null}

      {counts.total === 0 && !error ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-clinic-border bg-clinic-surface py-16 text-center">
          <Users className="mb-3 h-10 w-10 text-clinic-muted" />
          <p className="text-[12px] font-bold text-clinic-text">Nenhum paciente encontrado</p>
          <p className="mt-1 text-[10px] text-clinic-muted">
            Os pacientes serao importados automaticamente apos a sincronizacao com o sistema clinico.
          </p>
        </div>
      ) : pacientes.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-clinic-border bg-clinic-surface py-16 text-center">
          <Users className="mb-3 h-10 w-10 text-clinic-muted" />
          <p className="text-[12px] font-bold text-clinic-text">Nenhum paciente encontrado para &ldquo;{patientSearch.trim()}&rdquo;.</p>
          <button type="button" onClick={() => changeSearch('')} className="mt-3 text-[10px] font-bold text-clinic-primary hover:underline">Limpar pesquisa</button>
        </div>
      ) : (
        <DemoTable
          data={pacientes}
          getKey={(item) => item.id}
          columns={[
            {
              key: 'contato',
              label: 'Paciente',
              className: 'min-w-[260px] w-[28%]',
              render: (item) => <PatientContact paciente={item} />,
            },
            {
              key: 'tags',
              label: 'Tags',
              className: 'min-w-[250px] w-[27%]',
              render: (item) => (
                <PatientTags
                  paciente={item}
                  canManage={canManage}
                  busy={busy}
                  onManage={setSelectedPatient}
                  onRemove={removeTag}
                />
              ),
            },
            {
              key: 'status',
              label: 'Status',
              className: 'min-w-[130px] w-[14%]',
              render: (item) => (
                <StatusBadge tone={statusTone(item.status)}>{formatStatus(item.status)}</StatusBadge>
              ),
            },
            {
              key: 'telefone',
              label: 'Telefone',
              className: 'min-w-[160px] w-[18%]',
              render: (item) => (
                <span className="flex items-center gap-1.5 whitespace-nowrap font-semibold text-clinic-text">
                  <Phone className="h-3 w-3 text-clinic-muted" />
                  {item.telefone ?? '-'}
                </span>
              ),
            },
            {
              key: 'origem',
              label: 'Origem',
              className: 'min-w-[110px] w-[12%]',
              render: (item) => (
                <span className="text-[9px] text-clinic-muted">
                  {item.externalSource ?? 'Manual'}
                </span>
              ),
            },
          ]}
        />
      )}

      {totalPages > 1 ? (
        <nav aria-label="Paginacao de pacientes" className="mt-3 flex items-center justify-end gap-2 text-[10px] font-bold text-clinic-muted">
          <button
            type="button"
            disabled={page === 0 || searching}
            onClick={() => setPage((current) => Math.max(0, current - 1))}
            className="h-8 rounded-lg border border-clinic-border px-3 text-clinic-text hover:bg-clinic-hover disabled:opacity-40"
          >
            Anterior
          </button>
          <span>{page + 1} de {totalPages}</span>
          <button
            type="button"
            disabled={page + 1 >= totalPages || searching}
            onClick={() => setPage((current) => current + 1)}
            className="h-8 rounded-lg border border-clinic-border px-3 text-clinic-text hover:bg-clinic-hover disabled:opacity-40"
          >
            Proxima
          </button>
        </nav>
      ) : null}

      {selectedPatient && canManage ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4">
          <div className="w-full max-w-md rounded-lg border border-clinic-border bg-clinic-surface shadow-xl">
            <div className="flex items-start justify-between gap-3 border-b border-clinic-border px-4 py-3">
              <div>
                <h2 className="text-[14px] font-extrabold text-clinic-text">Adicionar tag</h2>
                <p className="text-[10px] text-clinic-muted">{selectedPatient.nome}</p>
              </div>
              <button
                type="button"
                aria-label="Fechar seletor de tags"
                onClick={() => setSelectedPatient(null)}
                className="flex h-8 w-8 items-center justify-center rounded-lg text-clinic-muted hover:bg-clinic-hover hover:text-clinic-text"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="space-y-3 p-4">
              <label className="block text-[10px] font-bold text-clinic-muted">
                Buscar tag
                <span className="mt-1 flex h-9 items-center gap-2 rounded-lg border border-clinic-border bg-clinic-input px-2">
                  <Search className="h-3.5 w-3.5 text-clinic-muted" />
                  <input
                    value={tagSearch}
                    onChange={(event) => setTagSearch(event.target.value)}
                    className="min-w-0 flex-1 bg-transparent text-[11px] text-clinic-text outline-none"
                  />
                </span>
              </label>

              {tagsToAdd.length === 0 ? (
                <p className="rounded-lg border border-dashed border-clinic-border px-3 py-4 text-center text-[10px] font-semibold text-clinic-muted">
                  Nenhuma tag ativa disponivel.
                </p>
              ) : (
                <div className="max-h-64 space-y-1 overflow-y-auto pr-1 custom-scrollbar">
                  {tagsToAdd.map((tagItem) => (
                    <button
                      key={tagItem.id}
                      type="button"
                      aria-label={`Adicionar ${tagItem.nome}`}
                      disabled={busy}
                      onClick={() => void addTag(tagItem.id)}
                      className="flex min-h-10 w-full items-center gap-2 rounded-lg border border-clinic-border px-3 text-left text-[11px] font-bold text-clinic-text hover:bg-clinic-hover disabled:opacity-50"
                    >
                      <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: tagItem.cor }} />
                      <span className="min-w-0 flex-1 truncate">{tagItem.nome}</span>
                      <Plus className="h-3.5 w-3.5 text-clinic-primary" />
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function PatientTags({
  paciente,
  canManage,
  busy,
  onManage,
  onRemove,
}: {
  paciente: PacienteResumo;
  canManage: boolean;
  busy: boolean;
  onManage: (paciente: PacienteResumo) => void;
  onRemove: (paciente: PacienteResumo, tagId: number) => Promise<void>;
}) {
  const tags = paciente.tags ?? [];

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {tags.length === 0 ? (
        <span className="inline-flex items-center gap-1 rounded-full border border-dashed border-clinic-border px-2 py-1 text-[9px] font-bold text-clinic-muted">
          <Tag className="h-3 w-3" />
          Sem tags
        </span>
      ) : (
        tags.map((tagItem) => (
          <span
            key={tagItem.id}
            className="inline-flex max-w-[130px] items-center gap-1 rounded-full border border-clinic-border bg-clinic-soft px-2 py-1 text-[9px] font-bold text-clinic-text"
          >
            <span className="h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: tagItem.cor }} />
            <span className="truncate">{tagItem.nome}</span>
            {canManage ? (
              <button
                type="button"
                aria-label={`Remover tag ${tagItem.nome} de ${paciente.nome}`}
                disabled={busy}
                onClick={() => void onRemove(paciente, tagItem.id)}
                className="shrink-0 text-clinic-muted hover:text-clinic-danger disabled:opacity-50"
              >
                <Trash2 className="h-3 w-3" />
              </button>
            ) : null}
          </span>
        ))
      )}

      {canManage ? (
        <button
          type="button"
          aria-label={`Adicionar tag para ${paciente.nome}`}
          disabled={busy}
          onClick={() => onManage(paciente)}
          className="inline-flex h-7 items-center gap-1 rounded-lg border border-clinic-border px-2 text-[9px] font-extrabold text-clinic-text hover:bg-clinic-hover disabled:opacity-50"
        >
          <Plus className="h-3 w-3" />
          Tag
        </button>
      ) : null}
    </div>
  );
}

function SummaryCard({
  icon: Icon,
  value,
  label,
}: {
  icon: LucideIcon;
  value: string;
  label: string;
}) {
  return (
    <section className="flex min-h-[64px] items-center gap-3 rounded-xl border border-clinic-border bg-clinic-surface px-3 shadow-[0_1px_2px_rgba(4,32,36,0.04)]">
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-clinic-primary/10 text-clinic-primary">
        <Icon className="h-4 w-4" />
      </div>
      <div className="min-w-0">
        <p className="truncate text-[16px] font-extrabold leading-5 text-clinic-text">{value}</p>
        <p className="truncate text-[8px] text-clinic-muted">{label}</p>
      </div>
    </section>
  );
}

function PatientContact({ paciente }: { paciente: PacienteResumo }) {
  return (
    <div className="flex items-center gap-2.5">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-clinic-primary/20 text-[10px] font-extrabold text-clinic-primary">
        {iniciais(paciente.nome)}
      </div>
      <div className="min-w-0">
        <p className="truncate text-[10px] font-extrabold text-clinic-text">{paciente.nome}</p>
        <p className="mt-0.5 flex items-center gap-1 truncate text-[8px] text-clinic-muted">
          <Mail className="h-2.5 w-2.5 shrink-0" />
          {paciente.externalId ? `ID ${paciente.externalId}` : 'Sem ID externo'}
        </p>
      </div>
    </div>
  );
}

function iniciais(nome: string) {
  return nome
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
}

function formatStatus(status: string) {
  const map: Record<string, string> = {
    EM_ATENDIMENTO: 'Em Atendimento',
    AGENDADO: 'Agendado',
    FINALIZADO: 'Finalizado',
    INATIVO: 'Inativo',
  };
  return map[status] ?? status;
}

function statusTone(status: string): 'green' | 'blue' | 'orange' | 'slate' {
  if (status === 'EM_ATENDIMENTO') return 'green';
  if (status === 'AGENDADO') return 'blue';
  if (status === 'FINALIZADO') return 'slate';
  return 'orange';
}

function errorMessage(cause: unknown) {
  return cause instanceof Error ? cause.message : 'Nao foi possivel concluir a operacao';
}
