package com.synapse.clinicafemina.integration.external;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.agenda.AgendaAgendamentoDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaConvenioDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaHorarioDisponivelDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaLocalDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaPacienteDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaProcedimentoDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaProfissionalDTO;
import com.synapse.clinicafemina.dto.agenda.AtualizarAgendamentoRequest;
import com.synapse.clinicafemina.dto.agenda.NovoAgendamentoRequest;
import com.synapse.clinicafemina.dto.agenda.NovoPacienteRequest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Abstração provider-agnostic da Agenda, usada pelo CRM para operar sobre o
 * provider real da clínica (Medware ou Darwin) sem que os consumidores (controller,
 * futuro frontend, N8N) precisem conhecer a diferença.
 */
public interface AgendaExternalProvider {

    ExternalProviderType providerType();

    /** Se o provider suporta listar todos os agendamentos da clínica por período (sem depender de CPF). */
    boolean supportsClinicWideListing();

    /** Se o provider suporta criação de encaixe (agendamento fora de grade/horário disponível). */
    boolean supportsFitIn();

    /** Se o provider suporta escrita real (criar/editar/cancelar refletindo no sistema externo). */
    boolean supportsWriteOperations();

    /** Se o provider expõe catálogo (profissionais/horários/procedimentos/convênios/locais). */
    boolean supportsCatalog();

    /** Se o provider suporta localizar/cadastrar paciente por CPF sob demanda. */
    boolean supportsPatientLookup();

    /** Se o provider suporta backfill administrativo do espelho local. */
    boolean supportsBackfill();

    /** Descreve a cobertura da listagem: FULL (toda a clínica) ou KNOWN_CRM_PATIENTS_ONLY. */
    String coverage();

    List<AgendaAgendamentoDTO> listarAgenda(Clinica clinica, OffsetDateTime inicio, OffsetDateTime fim);

    Optional<AgendaAgendamentoDTO> buscarPorId(Clinica clinica, Long agendamentoLocalId);

    List<AgendaAgendamentoDTO> listarPorPaciente(Clinica clinica, String cpf);

    List<AgendaProfissionalDTO> listarProfissionais(Clinica clinica);

    List<AgendaHorarioDisponivelDTO> listarHorariosDisponiveis(
            Clinica clinica, LocalDate data, List<String> profissionalIds);

    List<AgendaProcedimentoDTO> listarProcedimentos(Clinica clinica, String localId);

    List<AgendaConvenioDTO> listarConvenios(Clinica clinica, String localId);

    List<AgendaLocalDTO> listarLocais(Clinica clinica);

    Optional<AgendaPacienteDTO> buscarPaciente(Clinica clinica, String cpf);

    AgendaPacienteDTO criarOuLocalizarPaciente(Clinica clinica, NovoPacienteRequest dados);

    AgendaAgendamentoDTO criarAgendamento(Clinica clinica, NovoAgendamentoRequest dados);

    AgendaAgendamentoDTO criarEncaixe(Clinica clinica, NovoAgendamentoRequest dados);

    AgendaAgendamentoDTO atualizarAgendamento(Clinica clinica, Long agendamentoLocalId, AtualizarAgendamentoRequest dados);

    void cancelarAgendamento(Clinica clinica, Long agendamentoLocalId, String motivo);
}
