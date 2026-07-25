package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.agenda.AgendaAgendamentoDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaCapabilitiesDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaConvenioDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaHorarioDisponivelDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaLocalDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaPacienteDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaProcedimentoDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaProfissionalDTO;
import com.synapse.clinicafemina.dto.agenda.AtualizarAgendamentoRequest;
import com.synapse.clinicafemina.dto.agenda.NovoAgendamentoRequest;
import com.synapse.clinicafemina.dto.agenda.NovoPacienteRequest;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoCancelRequest;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoCreateRequest;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoResponse;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoUpdateRequest;
import com.synapse.clinicafemina.exception.AgendaOperationNotSupportedException;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.integration.external.AgendaExternalProvider;
import com.synapse.clinicafemina.integration.external.AgendaProviderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Camada provider-agnostic da Agenda: resolve o provider da clínica (Medware ou
 * Darwin) e delega. Para Medware (supportsWriteOperations=false), as operações de
 * escrita reaproveitam integralmente {@link AgendamentoService} já existente — nenhum
 * comportamento novo é introduzido para a UltraMedical.
 */
@Service
@RequiredArgsConstructor
public class AgendaService {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final String TIPO_PADRAO = "CONSULTA";

    private final AgendaProviderFactory providerFactory;
    private final AgendamentoService agendamentoService;

    public AgendaCapabilitiesDTO capacidades(Clinica clinica) {
        AgendaExternalProvider provider = provider(clinica);
        return new AgendaCapabilitiesDTO(
                provider.providerType().name(),
                provider.supportsCatalog(),
                provider.supportsWriteOperations(),
                provider.supportsFitIn(),
                provider.supportsClinicWideListing(),
                provider.supportsPatientLookup(),
                provider.supportsBackfill(),
                provider.coverage());
    }

    public List<AgendaAgendamentoDTO> listarAgenda(Clinica clinica, OffsetDateTime inicio, OffsetDateTime fim) {
        return provider(clinica).listarAgenda(clinica, inicio, fim);
    }

    public AgendaAgendamentoDTO buscarPorId(Clinica clinica, Long id) {
        return provider(clinica).buscarPorId(clinica, id)
                .orElseThrow(() -> new com.synapse.clinicafemina.exception.NotFoundException(
                        "Agendamento não encontrado"));
    }

    public List<AgendaAgendamentoDTO> listarPorPaciente(Clinica clinica, String cpf) {
        return provider(clinica).listarPorPaciente(clinica, cpf);
    }

    public List<AgendaProfissionalDTO> listarProfissionais(Clinica clinica) {
        return provider(clinica).listarProfissionais(clinica);
    }

    public List<AgendaHorarioDisponivelDTO> listarHorariosDisponiveis(
            Clinica clinica, LocalDate data, List<String> profissionalIds) {
        return provider(clinica).listarHorariosDisponiveis(clinica, data, profissionalIds);
    }

    public List<AgendaProcedimentoDTO> listarProcedimentos(Clinica clinica, String localId) {
        return provider(clinica).listarProcedimentos(clinica, localId);
    }

    public List<AgendaConvenioDTO> listarConvenios(Clinica clinica, String localId) {
        return provider(clinica).listarConvenios(clinica, localId);
    }

    public List<AgendaLocalDTO> listarLocais(Clinica clinica) {
        return provider(clinica).listarLocais(clinica);
    }

    public AgendaPacienteDTO criarOuLocalizarPaciente(Clinica clinica, NovoPacienteRequest dados) {
        return provider(clinica).criarOuLocalizarPaciente(clinica, dados);
    }

    public AgendaAgendamentoDTO criarAgendamento(Clinica clinica, NovoAgendamentoRequest dados) {
        AgendaExternalProvider provider = provider(clinica);
        if (!provider.supportsWriteOperations()) {
            return criarViaFluxoLocal(clinica, dados);
        }
        return provider.criarAgendamento(clinica, dados);
    }

    public AgendaAgendamentoDTO criarEncaixe(Clinica clinica, NovoAgendamentoRequest dados) {
        AgendaExternalProvider provider = provider(clinica);
        if (!provider.supportsFitIn()) {
            throw new AgendaOperationNotSupportedException(
                    "Encaixe não é suportado pelo provider desta clínica.");
        }
        return provider.criarEncaixe(clinica, dados);
    }

    public AgendaAgendamentoDTO atualizarAgendamento(
            Clinica clinica, Long agendamentoLocalId, AtualizarAgendamentoRequest dados) {
        AgendaExternalProvider provider = provider(clinica);
        if (!provider.supportsWriteOperations()) {
            return atualizarViaFluxoLocal(clinica, agendamentoLocalId, dados);
        }
        return provider.atualizarAgendamento(clinica, agendamentoLocalId, dados);
    }

    public void cancelarAgendamento(Clinica clinica, Long agendamentoLocalId, String motivo) {
        AgendaExternalProvider provider = provider(clinica);
        if (!provider.supportsWriteOperations()) {
            agendamentoService.cancelar(clinica, agendamentoLocalId, new AgendamentoCancelRequest(motivo));
            return;
        }
        provider.cancelarAgendamento(clinica, agendamentoLocalId, motivo);
    }

    private AgendaExternalProvider provider(Clinica clinica) {
        return providerFactory.getProvider(clinica.getExternalProvider());
    }

    private AgendaAgendamentoDTO criarViaFluxoLocal(Clinica clinica, NovoAgendamentoRequest dados) {
        if (dados.pacienteId() == null) {
            throw new BadRequestException("pacienteId é obrigatório para este provider.");
        }
        AgendamentoCreateRequest request = new AgendamentoCreateRequest(
                dados.pacienteId(),
                parseMedicoId(dados.profissionalId()),
                combinar(dados.data(), dados.horarioInicio()),
                combinar(dados.data(), dados.horarioFim()),
                TIPO_PADRAO,
                dados.procedimentoNome() == null || dados.procedimentoNome().isBlank()
                        ? "Agendamento" : dados.procedimentoNome());
        return mapLegacy(agendamentoService.criar(clinica, request));
    }

    private AgendaAgendamentoDTO atualizarViaFluxoLocal(
            Clinica clinica, Long agendamentoLocalId, AtualizarAgendamentoRequest dados) {
        AgendamentoUpdateRequest request = new AgendamentoUpdateRequest(
                null,
                null,
                combinar(dados.data(), dados.horarioInicio()),
                combinar(dados.data(), dados.horarioFim()),
                TIPO_PADRAO,
                dados.observacao() == null || dados.observacao().isBlank()
                        ? "Agendamento" : dados.observacao());
        return mapLegacy(agendamentoService.atualizar(clinica, agendamentoLocalId, request));
    }

    private Long parseMedicoId(String profissionalId) {
        if (profissionalId == null || profissionalId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(profissionalId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private OffsetDateTime combinar(LocalDate data, String horarioHHmm) {
        if (data == null || horarioHHmm == null || horarioHHmm.isBlank()) {
            return null;
        }
        return data.atTime(LocalTime.parse(horarioHHmm)).atZone(ZONA).toOffsetDateTime();
    }

    private AgendaAgendamentoDTO mapLegacy(AgendamentoResponse legacy) {
        return new AgendaAgendamentoDTO(
                legacy.id(),
                legacy.medicoExternalId(),
                legacy.origem(),
                legacy.pacienteId(),
                legacy.pacienteNome(),
                null,
                legacy.medicoId() == null ? legacy.medicoExternalId() : legacy.medicoId().toString(),
                legacy.medicoNome(),
                null,
                legacy.servicoNome(),
                null,
                null,
                null,
                null,
                legacy.dataHoraInicio() == null ? null : legacy.dataHoraInicio().toLocalDate(),
                legacy.dataHoraInicio() == null ? null : legacy.dataHoraInicio().toLocalTime().toString(),
                legacy.dataHoraFim() == null ? null : legacy.dataHoraFim().toLocalTime().toString(),
                legacy.status(),
                null,
                legacy.motivoCancelamento(),
                legacy.origem(),
                null,
                "SYNCED");
    }
}
