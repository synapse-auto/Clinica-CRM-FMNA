package com.synapse.clinicafemina.integration.external;

import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Usuario;
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
import com.synapse.clinicafemina.exception.AgendaOperationNotSupportedException;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.service.AgendaExternalDoctorResolver;
import com.synapse.clinicafemina.util.CpfUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementação Medware de {@link AgendaExternalProvider}: apenas leitura, delegando
 * inteiramente para a mesma tabela local já usada por {@code AgendamentoController}/
 * {@code AgendamentoService} — nenhum comportamento de leitura é reescrito, apenas
 * remapeado para o contrato normalizado. Escrita e catálogo sob demanda não existem
 * para Medware neste codebase (sem cliente HTTP de escrita, sem API de catálogo) e
 * lançam {@link AgendaOperationNotSupportedException} de forma explícita e sanitizada.
 */
@Component
@RequiredArgsConstructor
public class MedwareAgendaProvider implements AgendaExternalProvider {

    private static final String UNSUPPORTED_MESSAGE =
            "Operação não suportada para o provider Medware nesta versão do backend.";

    private final AgendamentoRepository agendamentoRepository;
    private final PacienteRepository pacienteRepository;
    private final AgendaExternalDoctorResolver externalDoctorResolver;

    @Override
    public ExternalProviderType providerType() {
        return ExternalProviderType.MEDWARE;
    }

    @Override
    public boolean supportsClinicWideListing() {
        return true;
    }

    @Override
    public boolean supportsFitIn() {
        return false;
    }

    @Override
    public boolean supportsWriteOperations() {
        return false;
    }

    @Override
    public boolean supportsCatalog() {
        return false;
    }

    @Override
    public boolean supportsPatientLookup() {
        return false;
    }

    @Override
    public boolean supportsBackfill() {
        return false;
    }

    @Override
    public String coverage() {
        return "FULL";
    }

    @Override
    public List<AgendaAgendamentoDTO> listarAgenda(Clinica clinica, OffsetDateTime inicio, OffsetDateTime fim) {
        return agendamentoRepository
                .findByClinicaIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                        clinica.getId(), inicio, fim)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public Optional<AgendaAgendamentoDTO> buscarPorId(Clinica clinica, Long agendamentoLocalId) {
        return agendamentoRepository.findByIdAndClinicaId(agendamentoLocalId, clinica.getId())
                .map(this::toDto);
    }

    @Override
    public List<AgendaAgendamentoDTO> listarPorPaciente(Clinica clinica, String cpf) {
        String cpfDigitos = CpfUtils.normalizarDigitos(cpf);
        String cpfHash = CpfUtils.hashSeguro(cpfDigitos);
        if (cpfHash == null) {
            return List.of();
        }
        return pacienteRepository.findByClinicaIdAndCpfHash(clinica.getId(), cpfHash)
                .map(paciente -> agendamentoRepository
                        .findByClinicaIdAndPacienteIdOrderByDataHoraInicioDesc(clinica.getId(), paciente.getId())
                        .stream()
                        .map(this::toDto)
                        .toList())
                .orElseGet(List::of);
    }

    @Override
    public List<AgendaProfissionalDTO> listarProfissionais(Clinica clinica) {
        throw new AgendaOperationNotSupportedException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public List<AgendaHorarioDisponivelDTO> listarHorariosDisponiveis(
            Clinica clinica, LocalDate data, List<String> profissionalIds) {
        throw new AgendaOperationNotSupportedException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public List<AgendaProcedimentoDTO> listarProcedimentos(Clinica clinica, String localId) {
        throw new AgendaOperationNotSupportedException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public List<AgendaConvenioDTO> listarConvenios(Clinica clinica, String localId) {
        throw new AgendaOperationNotSupportedException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public List<AgendaLocalDTO> listarLocais(Clinica clinica) {
        throw new AgendaOperationNotSupportedException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public Optional<AgendaPacienteDTO> buscarPaciente(Clinica clinica, String cpf) {
        throw new AgendaOperationNotSupportedException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public AgendaPacienteDTO criarOuLocalizarPaciente(Clinica clinica, NovoPacienteRequest dados) {
        throw new AgendaOperationNotSupportedException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public AgendaAgendamentoDTO criarAgendamento(Clinica clinica, NovoAgendamentoRequest dados) {
        throw new AgendaOperationNotSupportedException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public AgendaAgendamentoDTO criarEncaixe(Clinica clinica, NovoAgendamentoRequest dados) {
        throw new AgendaOperationNotSupportedException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public AgendaAgendamentoDTO atualizarAgendamento(
            Clinica clinica, Long agendamentoLocalId, AtualizarAgendamentoRequest dados) {
        throw new AgendaOperationNotSupportedException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public void cancelarAgendamento(Clinica clinica, Long agendamentoLocalId, String motivo) {
        throw new AgendaOperationNotSupportedException(UNSUPPORTED_MESSAGE);
    }

    private AgendaAgendamentoDTO toDto(Agendamento agendamento) {
        Paciente paciente = agendamento.getPaciente();
        String cpfMascarado = paciente == null
                ? null
                : CpfUtils.mascarar(CpfUtils.normalizarDigitos(paciente.getCpf()));
        String profissionalId = null;
        String profissionalNome = null;
        Usuario medico = agendamento.getMedico();
        if (medico != null) {
            profissionalId = medico.getId() == null ? null : medico.getId().toString();
            profissionalNome = medico.getNome();
        } else {
            AgendaExternalDoctorResolver.ExternalDoctor doctor = externalDoctorResolver.resolve(agendamento)
                    .orElse(null);
            if (doctor != null) {
                profissionalId = doctor.codigoExterno();
                profissionalNome = doctor.nome();
            }
        }
        return new AgendaAgendamentoDTO(
                agendamento.getId(),
                agendamento.getExternalId(),
                agendamento.getExternalSource() == null ? null : agendamento.getExternalSource().name(),
                paciente == null ? null : paciente.getId(),
                paciente == null ? null : paciente.getNome(),
                cpfMascarado,
                profissionalId,
                profissionalNome,
                null,
                agendamento.getServicoNome(),
                null,
                null,
                null,
                null,
                agendamento.getDataHoraInicio() == null ? null : agendamento.getDataHoraInicio().toLocalDate(),
                agendamento.getDataHoraInicio() == null ? null : agendamento.getDataHoraInicio().toLocalTime().toString(),
                agendamento.getDataHoraFim() == null ? null : agendamento.getDataHoraFim().toLocalTime().toString(),
                agendamento.getStatus(),
                null,
                null,
                agendamento.getOrigem(),
                agendamento.getSincronizadoEm(),
                agendamento.getSyncStatus()
        );
    }
}
