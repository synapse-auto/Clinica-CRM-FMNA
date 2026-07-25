'use client';

import { useEffect, useState, type FormEvent } from 'react';
import { AlertTriangle, X } from 'lucide-react';
import { DatePicker } from '@/components/ui/date-picker';
import { FormSelect, SearchableSelect } from '@/components/ui/form-select';
import {
  atualizarAgendamento,
  cancelarAgendamento,
  criarAgendamento,
  criarEncaixe,
  criarOuLocalizarPaciente,
  listarConvenios,
  listarHorariosDisponiveis,
  listarLocais,
  listarPacientesMedware,
  listarProcedimentos,
} from '@/services/agenda';
import type {
  AgendaAgendamento,
  AgendaConvenio,
  AgendaHorarioDisponivel,
  AgendaLocal,
  AgendaProcedimento,
  AgendaProfissional,
} from '@/types/agenda';
import type { PacienteResumo } from '@/types/paciente';

type AgendamentoModalProps = {
  appointment: AgendaAgendamento | null;
  profissionais: AgendaProfissional[];
  hasCatalog: boolean;
  fitIn?: boolean;
  initialCancelMode?: boolean;
  weekStart: string;
  onClose: () => void;
  onSaved: (updated: AgendaAgendamento) => void;
};

const inputClassName = 'h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-xs text-clinic-text outline-none transition focus:border-clinic-primary disabled:cursor-not-allowed disabled:opacity-60';
const secondaryButtonClassName = 'rounded-lg border border-clinic-border bg-clinic-surface px-3 py-2 text-[10px] font-bold text-clinic-text transition hover:bg-clinic-hover';

export function AgendamentoModal({
  appointment,
  profissionais,
  hasCatalog,
  fitIn = false,
  initialCancelMode = false,
  weekStart,
  onClose,
  onSaved,
}: AgendamentoModalProps) {
  const isEdit = appointment !== null;

  // Paciente
  const [pacienteId, setPacienteId] = useState<number | null>(appointment?.pacienteId ?? null);
  const [pacienteNome, setPacienteNome] = useState(appointment?.pacienteNome ?? '');
  const [cpf, setCpf] = useState('');
  const [nomeNovoPaciente, setNomeNovoPaciente] = useState('');
  const [telefoneNovoPaciente, setTelefoneNovoPaciente] = useState('');
  const [buscandoPaciente, setBuscandoPaciente] = useState(false);
  const [pacientesMedware, setPacientesMedware] = useState<PacienteResumo[]>([]);

  // Profissional / catalogo
  const [profissionalId, setProfissionalId] = useState(appointment?.profissionalId ?? '');
  const [data, setData] = useState(appointment?.data ?? weekStart);
  const [horarios, setHorarios] = useState<AgendaHorarioDisponivel[]>([]);
  const [horariosLoading, setHorariosLoading] = useState(false);
  const [selectedHorario, setSelectedHorario] = useState<AgendaHorarioDisponivel | null>(null);
  const [horarioInicioManual, setHorarioInicioManual] = useState(appointment?.horarioInicio ?? '09:00');
  const [horarioFimManual, setHorarioFimManual] = useState(appointment?.horarioFim ?? '09:30');
  const [locais, setLocais] = useState<AgendaLocal[]>([]);
  const [localId, setLocalId] = useState(appointment?.localId ?? '');
  const [procedimentos, setProcedimentos] = useState<AgendaProcedimento[]>([]);
  const [procedimentoId, setProcedimentoId] = useState(appointment?.procedimentoId ?? '');
  const [procedimentoTexto, setProcedimentoTexto] = useState(appointment?.procedimentoNome ?? '');
  const [convenios, setConvenios] = useState<AgendaConvenio[]>([]);
  const [convenioId, setConvenioId] = useState(appointment?.convenioId ?? '');
  const [observacao, setObservacao] = useState(appointment?.observacao ?? '');

  const [cancelMode, setCancelMode] = useState(initialCancelMode);
  const [cancelReason, setCancelReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successNotice, setSuccessNotice] = useState<AgendaAgendamento | null>(null);
  const isCanceled = appointment?.status === 'CANCELADO';
  const localSelecionado = fitIn ? localId : (selectedHorario?.localId ?? localId);

  useEffect(() => {
    if (hasCatalog) return;
    listarPacientesMedware().then(setPacientesMedware).catch(() => setPacientesMedware([]));
  }, [hasCatalog]);

  useEffect(() => {
    if (!hasCatalog || fitIn || !profissionalId || !data) {
      setHorarios([]);
      return;
    }
    setHorariosLoading(true);
    setSelectedHorario(null);
    listarHorariosDisponiveis(data, [profissionalId])
      .then(setHorarios)
      .catch(() => setHorarios([]))
      .finally(() => setHorariosLoading(false));
  }, [hasCatalog, fitIn, profissionalId, data]);

  useEffect(() => {
    if (!hasCatalog || fitIn) {
      listarLocais().then(setLocais).catch(() => setLocais([]));
    }
  }, [hasCatalog, fitIn]);

  useEffect(() => {
    if (!hasCatalog || !localSelecionado) {
      setProcedimentos([]);
      setConvenios([]);
      return;
    }
    listarProcedimentos(localSelecionado).then(setProcedimentos).catch(() => setProcedimentos([]));
    listarConvenios(localSelecionado).then(setConvenios).catch(() => setConvenios([]));
  }, [hasCatalog, localSelecionado]);

  async function handleBuscarPaciente() {
    if (!cpf.trim() || !nomeNovoPaciente.trim()) {
      setError('Informe CPF e nome para localizar ou cadastrar o paciente.');
      return;
    }
    setBuscandoPaciente(true);
    setError(null);
    try {
      const resultado = await criarOuLocalizarPaciente({
        cpf: cpf.trim(),
        nome: nomeNovoPaciente.trim(),
        telefone: telefoneNovoPaciente.trim() || null,
        email: null,
        dataNascimento: null,
      });
      setPacienteId(resultado.id);
      setPacienteNome(resultado.nome);
    } catch (caughtError) {
      setError(friendlyMessage(caughtError, 'Não foi possível localizar ou cadastrar o paciente.'));
    } finally {
      setBuscandoPaciente(false);
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setError(null);

    if (!pacienteId && !isEdit) {
      setError('Selecione ou cadastre um paciente antes de confirmar.');
      return;
    }

    setBusy(true);
    try {
      if (isEdit && appointment) {
        const updated = await atualizarAgendamento(appointment.idLocal, {
          status: null,
          data,
          horarioInicio: hasCatalog ? (selectedHorario?.horarioInicio ?? horarioInicioManual) : horarioInicioManual,
          horarioFim: hasCatalog ? (selectedHorario?.horarioFim ?? horarioFimManual) : horarioFimManual,
          timetableId: hasCatalog ? (selectedHorario?.timetableId ?? appointment.timetableId) : null,
          procedimentoId: hasCatalog ? (procedimentoId || null) : null,
          convenioId: hasCatalog ? (convenioId || null) : null,
          observacao: hasCatalog ? (observacao || null) : (procedimentoTexto || null),
        });
        finishWithResult(updated);
        return;
      }

      if (hasCatalog && !fitIn && !selectedHorario) {
        setError('Selecione um horário disponível antes de confirmar.');
        setBusy(false);
        return;
      }

      const payload = {
        pacienteId,
        pacienteCpf: null,
        profissionalId: hasCatalog ? (profissionalId || null) : null,
        localId: hasCatalog ? (localSelecionado || null) : null,
        timetableId: hasCatalog && !fitIn ? (selectedHorario?.timetableId ?? null) : null,
        data,
        horarioInicio: hasCatalog ? (fitIn ? horarioInicioManual : (selectedHorario?.horarioInicio ?? '')) : horarioInicioManual,
        horarioFim: hasCatalog ? (fitIn ? horarioFimManual : (selectedHorario?.horarioFim ?? null)) : horarioFimManual,
        procedimentoId: hasCatalog ? (procedimentoId || null) : null,
        procedimentoNome: hasCatalog ? (procedimentoTexto || null) : (procedimentoTexto || 'Agendamento'),
        convenioId: hasCatalog ? (convenioId || null) : null,
        observacao: observacao || null,
      };

      const created = fitIn
        ? await criarEncaixe(payload)
        : await criarAgendamento(payload);
      finishWithResult(created);
    } catch (caughtError) {
      setError(friendlyMessage(caughtError, 'Não foi possível salvar o agendamento.'));
    } finally {
      setBusy(false);
    }
  }

  function finishWithResult(result: AgendaAgendamento) {
    if (result.syncStatus === 'PENDING_RECONCILIATION') {
      setSuccessNotice(result);
      return;
    }
    onSaved(result);
  }

  async function handleCancel() {
    if (!appointment || busy || !cancelReason.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await cancelarAgendamento(appointment.idLocal, cancelReason.trim());
      onSaved({ ...appointment, status: 'CANCELADO' });
    } catch (caughtError) {
      setError(friendlyMessage(caughtError, 'Não foi possível cancelar este agendamento.'));
      setBusy(false);
    }
  }

  if (successNotice) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 p-4">
        <section role="dialog" aria-modal="true" className="w-full max-w-md rounded-xl border border-clinic-border bg-clinic-surface p-5 shadow-2xl">
          <h2 className="text-sm font-extrabold text-clinic-text">Agendamento enviado</h2>
          <p className="mt-2 flex items-start gap-1.5 rounded-lg bg-clinic-canvas px-3 py-2 text-[11px] text-clinic-text">
            <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-clinic-warning" />
            Este agendamento foi enviado e ainda aguarda confirmação da integração. Ele já aparece
            na agenda com o status &quot;Aguardando confirmação&quot; — use o botão &quot;Atualizar situação&quot;
            para conferir quando for confirmado.
          </p>
          <div className="mt-4 flex justify-end">
            <button
              type="button"
              onClick={() => onSaved(successNotice)}
              className="rounded-lg bg-clinic-primary px-4 py-2 text-[10px] font-bold text-white"
            >
              Entendi
            </button>
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 p-4">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="agendamento-modal-title"
        className="max-h-[92vh] w-full max-w-xl overflow-auto rounded-xl border border-clinic-border bg-clinic-surface shadow-2xl custom-scrollbar"
      >
        <div className="flex items-center justify-between border-b border-clinic-border px-5 py-4">
          <div>
            <h2 id="agendamento-modal-title" className="text-sm font-extrabold text-clinic-text">
              {isEdit ? 'Editar agendamento' : fitIn ? 'Novo encaixe' : 'Novo agendamento'}
            </h2>
            <p className="mt-0.5 text-[10px] text-clinic-muted">
              Os dados serão enviados diretamente para a agenda da clínica.
            </p>
          </div>
          <button
            type="button"
            aria-label="Fechar"
            onClick={onClose}
            className="rounded-lg p-2 text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-text"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="grid grid-cols-1 gap-3 p-5 sm:grid-cols-2">
          {error ? (
            <p role="alert" className="sm:col-span-2 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[10px] font-semibold text-clinic-danger">
              {error}
            </p>
          ) : null}

          {fitIn ? (
            <p className="sm:col-span-2 flex items-start gap-1.5 rounded-lg border border-clinic-warning/30 bg-clinic-warning/10 px-3 py-2 text-[10px] font-semibold text-clinic-text">
              <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-clinic-warning" />
              Encaixes podem sobrepor horários existentes.
            </p>
          ) : null}

          {isEdit ? (
            <Field label="Paciente" className="sm:col-span-2">
              <p className={`${inputClassName} flex items-center`}>{pacienteNome}</p>
            </Field>
          ) : hasCatalog ? (
            <>
              <Field label="CPF do paciente" htmlFor="agenda-cpf">
                <input
                  id="agenda-cpf"
                  value={cpf}
                  onChange={(event) => setCpf(event.target.value)}
                  disabled={busy || Boolean(pacienteId)}
                  placeholder="Somente números"
                  className={inputClassName}
                />
              </Field>
              <Field label="Nome do paciente" htmlFor="agenda-nome-paciente">
                <input
                  id="agenda-nome-paciente"
                  value={nomeNovoPaciente}
                  onChange={(event) => setNomeNovoPaciente(event.target.value)}
                  disabled={busy || Boolean(pacienteId)}
                  className={inputClassName}
                />
              </Field>
              <Field label="Telefone (opcional)" htmlFor="agenda-telefone-paciente">
                <input
                  id="agenda-telefone-paciente"
                  value={telefoneNovoPaciente}
                  onChange={(event) => setTelefoneNovoPaciente(event.target.value)}
                  disabled={busy || Boolean(pacienteId)}
                  className={inputClassName}
                />
              </Field>
              <div className="flex items-end">
                {pacienteId ? (
                  <p className="text-[10px] font-bold text-clinic-primary">
                    Paciente selecionado: {pacienteNome}
                  </p>
                ) : (
                  <button
                    type="button"
                    onClick={handleBuscarPaciente}
                    disabled={buscandoPaciente || busy}
                    className="h-10 rounded-lg bg-clinic-primary px-3 text-[10px] font-bold text-white disabled:opacity-50"
                  >
                    {buscandoPaciente ? 'Buscando...' : 'Buscar/Cadastrar paciente'}
                  </button>
                )}
              </div>
            </>
          ) : (
            <Field label="Paciente" htmlFor="agenda-paciente" className="sm:col-span-2">
              <SearchableSelect
                id="agenda-paciente"
                aria-label="Paciente"
                value={pacienteId ? String(pacienteId) : ''}
                onValueChange={(value) => setPacienteId(value ? Number(value) : null)}
                required
                disabled={busy}
                className={inputClassName}
                placeholder="Selecione um paciente"
                options={pacientesMedware.map((paciente) => ({ value: String(paciente.id), label: paciente.nome }))}
              />
            </Field>
          )}

          {hasCatalog && !isEdit ? (
            <Field label="Profissional" htmlFor="agenda-profissional" className="sm:col-span-2">
              <FormSelect
                id="agenda-profissional"
                aria-label="Profissional"
                value={profissionalId}
                onValueChange={setProfissionalId}
                required
                disabled={busy}
                className={inputClassName}
                options={profissionais.map((profissional) => ({ value: profissional.id, label: profissional.nome }))}
              />
            </Field>
          ) : hasCatalog && isEdit ? (
            <Field label="Profissional" className="sm:col-span-2">
              <p className={`${inputClassName} flex items-center`}>{appointment?.profissionalNome ?? 'Sem profissional'}</p>
            </Field>
          ) : null}

          <Field label="Data" htmlFor="agenda-data">
            <DatePicker
              id="agenda-data"
              value={data}
              onValueChange={setData}
              required
              disabled={busy || isCanceled}
              className={inputClassName}
            />
          </Field>

          {hasCatalog && fitIn ? (
            <Field label="Local" htmlFor="agenda-local">
              <FormSelect
                id="agenda-local"
                aria-label="Local"
                value={localId}
                onValueChange={setLocalId}
                required
                disabled={busy}
                className={inputClassName}
                options={locais.map((local) => ({ value: local.id, label: local.nome }))}
              />
            </Field>
          ) : null}

          {hasCatalog && (fitIn || !profissionalId) ? null : hasCatalog ? (
            <Field label="Horário disponível" className="sm:col-span-2">
              {horariosLoading ? (
                <p className="text-[10px] text-clinic-muted">Buscando horários...</p>
              ) : horarios.length === 0 ? (
                <p className="text-[10px] text-clinic-muted">Nenhum horário disponível para essa data/profissional.</p>
              ) : (
                <div className="flex flex-wrap gap-1.5">
                  {horarios.map((horario) => (
                    <button
                      key={horario.timetableId}
                      type="button"
                      onClick={() => { setSelectedHorario(horario); setLocalId(horario.localId ?? ''); }}
                      disabled={busy}
                      className={selectedHorario?.timetableId === horario.timetableId
                        ? 'rounded-lg bg-clinic-primary px-2.5 py-1.5 text-[10px] font-bold text-white'
                        : 'rounded-lg border border-clinic-border bg-clinic-surface px-2.5 py-1.5 text-[10px] font-semibold text-clinic-text transition hover:bg-clinic-hover'}
                    >
                      {horario.horarioInicio}
                    </button>
                  ))}
                </div>
              )}
            </Field>
          ) : null}

          {(!hasCatalog || fitIn) ? (
            <>
              <Field label="Horário inicial" htmlFor="agenda-horario-inicio">
                <input
                  id="agenda-horario-inicio"
                  type="time"
                  value={horarioInicioManual}
                  onChange={(event) => setHorarioInicioManual(event.target.value)}
                  required
                  disabled={busy || isCanceled}
                  className={inputClassName}
                />
              </Field>
              <Field label="Horário final" htmlFor="agenda-horario-fim">
                <input
                  id="agenda-horario-fim"
                  type="time"
                  value={horarioFimManual}
                  onChange={(event) => setHorarioFimManual(event.target.value)}
                  disabled={busy || isCanceled}
                  className={inputClassName}
                />
              </Field>
            </>
          ) : null}

          {hasCatalog ? (
            <>
              <Field label="Procedimento" htmlFor="agenda-procedimento">
                <FormSelect
                  id="agenda-procedimento"
                  aria-label="Procedimento"
                  value={procedimentoId}
                  onValueChange={(value) => {
                    setProcedimentoId(value);
                    setProcedimentoTexto(procedimentos.find((item) => item.id === value)?.nome ?? '');
                  }}
                  disabled={busy || isCanceled}
                  className={inputClassName}
                  placeholder="Selecione um procedimento"
                  options={procedimentos.map((procedimento) => ({ value: procedimento.id, label: procedimento.nome }))}
                />
              </Field>
              <Field label="Convênio" htmlFor="agenda-convenio">
                <FormSelect
                  id="agenda-convenio"
                  aria-label="Convênio"
                  value={convenioId}
                  onValueChange={setConvenioId}
                  disabled={busy || isCanceled}
                  className={inputClassName}
                  placeholder="Particular / não informado"
                  options={convenios.map((convenio) => ({ value: convenio.id, label: convenio.nome }))}
                />
              </Field>
            </>
          ) : (
            <Field label="Procedimento" htmlFor="agenda-procedimento-texto" className="sm:col-span-2">
              <input
                id="agenda-procedimento-texto"
                value={procedimentoTexto}
                onChange={(event) => setProcedimentoTexto(event.target.value)}
                placeholder="Ex.: Consulta pré-natal"
                maxLength={120}
                disabled={busy || isCanceled}
                className={inputClassName}
              />
            </Field>
          )}

          <Field label="Observação (opcional)" htmlFor="agenda-observacao" className="sm:col-span-2">
            <input
              id="agenda-observacao"
              value={observacao ?? ''}
              onChange={(event) => setObservacao(event.target.value)}
              maxLength={255}
              disabled={busy || isCanceled}
              className={inputClassName}
            />
          </Field>

          {cancelMode ? (
            <div className="sm:col-span-2 rounded-lg border border-clinic-danger/30 bg-clinic-danger/5 p-3">
              <label htmlFor="agenda-cancelamento" className="text-[10px] font-bold text-clinic-text">
                Motivo do cancelamento
              </label>
              <input
                id="agenda-cancelamento"
                value={cancelReason}
                onChange={(event) => setCancelReason(event.target.value)}
                maxLength={255}
                className={`${inputClassName} mt-1.5`}
              />
              <div className="mt-3 flex justify-end gap-2">
                <button type="button" onClick={() => setCancelMode(false)} className={secondaryButtonClassName}>
                  Voltar
                </button>
                <button
                  type="button"
                  disabled={busy || !cancelReason.trim()}
                  onClick={handleCancel}
                  className="rounded-lg bg-clinic-danger px-3 py-2 text-[10px] font-bold text-white disabled:opacity-50"
                >
                  {busy ? 'Cancelando...' : 'Confirmar cancelamento'}
                </button>
              </div>
            </div>
          ) : null}

          <div className="sm:col-span-2 mt-1 flex flex-wrap items-center justify-between gap-2 border-t border-clinic-border pt-4">
            <div>
              {isEdit && !isCanceled && !cancelMode ? (
                <button
                  type="button"
                  onClick={() => setCancelMode(true)}
                  className="rounded-lg px-3 py-2 text-[10px] font-bold text-clinic-danger transition hover:bg-clinic-danger/10"
                >
                  Cancelar agendamento
                </button>
              ) : null}
              {isCanceled ? (
                <span className="text-[10px] font-bold text-clinic-danger">Agendamento cancelado</span>
              ) : null}
            </div>
            <div className="flex gap-2">
              <button type="button" onClick={onClose} className={secondaryButtonClassName}>
                Fechar
              </button>
              {!isCanceled && !cancelMode ? (
                <button
                  type="submit"
                  disabled={busy}
                  className="rounded-lg bg-clinic-primary px-4 py-2 text-[10px] font-bold text-white disabled:opacity-50"
                >
                  {busy ? 'Salvando...' : 'Salvar agendamento'}
                </button>
              ) : null}
            </div>
          </div>
        </form>
      </section>
    </div>
  );
}

function Field({
  label,
  htmlFor,
  className = '',
  children,
}: {
  label: string;
  htmlFor?: string;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <div className={className}>
      <label htmlFor={htmlFor} className="mb-1.5 block text-[10px] font-bold text-clinic-text">
        {label}
      </label>
      {children}
    </div>
  );
}

function friendlyMessage(caughtError: unknown, fallback: string): string {
  return caughtError instanceof Error && caughtError.message ? caughtError.message : fallback;
}
