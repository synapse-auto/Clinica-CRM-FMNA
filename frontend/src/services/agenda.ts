import type {
  AgendaAgendamento,
  AgendaCapabilities,
  AgendaConvenio,
  AgendaHorarioDisponivel,
  AgendaLocal,
  AgendaPaciente,
  AgendaProcedimento,
  AgendaProfissional,
  AtualizarAgendamentoPayload,
  NovoAgendamentoPayload,
  NovoPacientePayload,
} from '@/types/agenda';
import type { PacienteResumo } from '@/types/paciente';

// Medware nao suporta o catalogo de pacientes de /api/agenda/pacientes (501) — a
// selecao de paciente nesse caso reaproveita a listagem ja existente em /api/pacientes.
export function listarPacientesMedware(): Promise<PacienteResumo[]> {
  return requestJson('/api/pacientes');
}

// Capacidades reais do provider resolvido no backend. Nunca inferir a partir do
// sucesso/falha de outras chamadas (ex.: catalogo) — sempre consultar este endpoint.
export function obterCapacidades(): Promise<AgendaCapabilities> {
  return requestJson('/api/agenda/capabilities');
}

export function listarAgenda(startDate: string, endDate: string): Promise<AgendaAgendamento[]> {
  const params = new URLSearchParams({ startDate, endDate });
  return requestJson(`/api/agenda?${params.toString()}`);
}

export function buscarAgendamento(id: number): Promise<AgendaAgendamento> {
  return requestJson(`/api/agenda/${id}`);
}

export function listarPorPaciente(cpf: string): Promise<AgendaAgendamento[]> {
  const params = new URLSearchParams({ cpf });
  return requestJson(`/api/agenda/paciente?${params.toString()}`);
}

export function listarProfissionais(): Promise<AgendaProfissional[]> {
  return requestJson('/api/agenda/profissionais');
}

export function listarHorariosDisponiveis(
  date: string,
  professionalId?: string[],
): Promise<AgendaHorarioDisponivel[]> {
  const params = new URLSearchParams({ date });
  professionalId?.forEach((id) => params.append('professionalId', id));
  return requestJson(`/api/agenda/horarios?${params.toString()}`);
}

export function listarProcedimentos(localId?: string): Promise<AgendaProcedimento[]> {
  const params = localId ? `?${new URLSearchParams({ localId }).toString()}` : '';
  return requestJson(`/api/agenda/procedimentos${params}`);
}

export function listarConvenios(localId?: string): Promise<AgendaConvenio[]> {
  const params = localId ? `?${new URLSearchParams({ localId }).toString()}` : '';
  return requestJson(`/api/agenda/convenios${params}`);
}

export function listarLocais(): Promise<AgendaLocal[]> {
  return requestJson('/api/agenda/locais');
}

export function criarOuLocalizarPaciente(payload: NovoPacientePayload): Promise<AgendaPaciente> {
  return requestJson('/api/agenda/pacientes', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function criarAgendamento(payload: NovoAgendamentoPayload): Promise<AgendaAgendamento> {
  return requestJson('/api/agenda', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function criarEncaixe(payload: NovoAgendamentoPayload): Promise<AgendaAgendamento> {
  return requestJson('/api/agenda/encaixe', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function atualizarAgendamento(
  id: number,
  payload: AtualizarAgendamentoPayload,
): Promise<AgendaAgendamento> {
  return requestJson(`/api/agenda/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export async function cancelarAgendamento(id: number, motivo: string): Promise<void> {
  const params = new URLSearchParams({ motivo });
  const response = await fetch(`/api/agenda/${id}?${params.toString()}`, {
    method: 'DELETE',
    headers: { Accept: 'application/json' },
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
}

async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...init.headers,
    },
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  return response.json() as Promise<T>;
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json() as { message?: string };
    return body.message ?? `Não foi possível concluir a operação (${response.status})`;
  } catch {
    return `Não foi possível concluir a operação (${response.status})`;
  }
}
