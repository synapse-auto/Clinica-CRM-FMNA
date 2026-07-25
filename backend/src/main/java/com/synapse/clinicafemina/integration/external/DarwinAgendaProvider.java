package com.synapse.clinicafemina.integration.external;

import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
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
import com.synapse.clinicafemina.dto.darwin.DarwinCreateFitInScheduleRequest;
import com.synapse.clinicafemina.dto.darwin.DarwinCreatePatientRequest;
import com.synapse.clinicafemina.dto.darwin.DarwinCreateScheduleRequest;
import com.synapse.clinicafemina.dto.darwin.DarwinInsuranceProcedureRef;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientRecordDTO;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientScheduleResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinUpdateScheduleRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.DarwinIntegrationException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.integration.DarwinClient;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.service.DarwinAgendaSyncService;
import com.synapse.clinicafemina.service.DarwinConsultaService;
import com.synapse.clinicafemina.util.CpfUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementação Darwin de {@link AgendaExternalProvider}: catálogo sob demanda via
 * {@link DarwinConsultaService} (leitura, já existente) e escrita real via
 * {@link DarwinClient} (novos métodos desta sessão), com espelho local mantido por
 * {@link DarwinAgendaSyncService}. A Darwin não permite listar toda a agenda da
 * clínica por período — apenas por CPF — então {@link #listarAgenda} lê exclusivamente
 * o espelho local (cobertura limitada a pacientes já conhecidos pelo CRM).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DarwinAgendaProvider implements AgendaExternalProvider {

    private final DarwinClient darwinClient;
    private final DarwinConsultaService darwinConsultaService;
    private final DarwinAgendaSyncService syncService;
    private final AgendamentoRepository agendamentoRepository;
    private final PacienteRepository pacienteRepository;

    @Override
    public ExternalProviderType providerType() {
        return ExternalProviderType.DARWIN;
    }

    @Override
    public boolean supportsClinicWideListing() {
        return false;
    }

    @Override
    public boolean supportsFitIn() {
        return true;
    }

    @Override
    public boolean supportsWriteOperations() {
        return true;
    }

    @Override
    public boolean supportsCatalog() {
        return true;
    }

    @Override
    public boolean supportsPatientLookup() {
        return true;
    }

    @Override
    public boolean supportsBackfill() {
        return true;
    }

    @Override
    public String coverage() {
        return "KNOWN_CRM_PATIENTS_ONLY";
    }

    @Override
    public List<AgendaAgendamentoDTO> listarAgenda(Clinica clinica, java.time.OffsetDateTime inicio, java.time.OffsetDateTime fim) {
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
        if (!CpfUtils.valido(cpfDigitos)) {
            throw new BadRequestException("CPF invalido.");
        }
        Paciente paciente = localizarOuCriarPacienteLocal(clinica, cpfDigitos, null, null);
        DarwinPatientScheduleResponse resposta = darwinConsultaService.listarAgendamentosPorCpf(
                CpfUtils.formatarComMascara(cpfDigitos), null, null, null);
        return syncService.sincronizarAgendamentosDoPaciente(clinica, paciente, resposta).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<AgendaProfissionalDTO> listarProfissionais(Clinica clinica) {
        return darwinConsultaService.listarProfissionaisDoLocal().stream()
                .map(p -> new AgendaProfissionalDTO(p.professionalId(), p.professionalName(), "DARWIN"))
                .toList();
    }

    @Override
    public List<AgendaHorarioDisponivelDTO> listarHorariosDisponiveis(
            Clinica clinica, LocalDate data, List<String> profissionalIds) {
        var resposta = darwinConsultaService.listarHorariosDisponiveis(data, profissionalIds);
        List<AgendaHorarioDisponivelDTO> resultado = new ArrayList<>();
        if (resposta.professionalsAvailableTimes() != null) {
            for (var prof : resposta.professionalsAvailableTimes()) {
                if (prof.timetables() == null) {
                    continue;
                }
                for (var tt : prof.timetables()) {
                    if (tt.availableTimes() == null) {
                        continue;
                    }
                    for (var horario : tt.availableTimes()) {
                        resultado.add(new AgendaHorarioDisponivelDTO(
                                tt.timetableId(), prof.professionalId(), prof.professionalName(),
                                tt.locationId(), tt.locationName(), data,
                                horario.startTime(), horario.endTime()));
                    }
                }
            }
        }
        return resultado;
    }

    @Override
    public List<AgendaProcedimentoDTO> listarProcedimentos(Clinica clinica, String localId) {
        var resposta = darwinConsultaService.listarProcedimentos(localId, null, null, null);
        return resposta.procedures() == null
                ? List.of()
                : resposta.procedures().stream().map(p -> new AgendaProcedimentoDTO(p.id(), p.name())).toList();
    }

    @Override
    public List<AgendaConvenioDTO> listarConvenios(Clinica clinica, String localId) {
        var resposta = darwinConsultaService.listarConvenios(localId, null, null, null);
        return resposta.insurances() == null
                ? List.of()
                : resposta.insurances().stream().map(i -> new AgendaConvenioDTO(i.id(), i.name())).toList();
    }

    @Override
    public List<AgendaLocalDTO> listarLocais(Clinica clinica) {
        return darwinConsultaService.listarLocaisDoProfissional().stream()
                .map(l -> new AgendaLocalDTO(l.locationId(), l.locationName()))
                .toList();
    }

    @Override
    public Optional<AgendaPacienteDTO> buscarPaciente(Clinica clinica, String cpf) {
        String cpfDigitos = CpfUtils.normalizarDigitos(cpf);
        if (!CpfUtils.valido(cpfDigitos)) {
            throw new BadRequestException("CPF invalido.");
        }
        try {
            DarwinPatientRecordDTO paciente = darwinConsultaService.buscarPacientePorCpf(
                    CpfUtils.formatarComMascara(cpfDigitos));
            return Optional.of(new AgendaPacienteDTO(null, paciente.name(), CpfUtils.mascarar(cpfDigitos)));
        } catch (DarwinIntegrationException e) {
            if (e.upstreamStatus() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public AgendaPacienteDTO criarOuLocalizarPaciente(Clinica clinica, NovoPacienteRequest dados) {
        String cpfDigitos = CpfUtils.normalizarDigitos(dados.cpf());
        if (!CpfUtils.valido(cpfDigitos)) {
            throw new BadRequestException("CPF invalido.");
        }
        String cpfFormatado = CpfUtils.formatarComMascara(cpfDigitos);
        DarwinCreatePatientRequest request = new DarwinCreatePatientRequest(
                dados.nome(), cpfFormatado, null, null, null, dados.email(), null, null, null, null, null,
                dados.telefone(), null, null,
                dados.dataNascimento() == null ? null : dados.dataNascimento() + "T00:00:00.000Z", null);
        var resposta = callDarwin(() -> darwinClient.criarPaciente(request));
        Paciente pacienteLocal = localizarOuCriarPacienteLocal(clinica, cpfDigitos, dados.nome(), dados);
        String nome = resposta.patient() == null ? dados.nome() : resposta.patient().name();
        return new AgendaPacienteDTO(pacienteLocal.getId(), nome, CpfUtils.mascarar(cpfDigitos));
    }

    @Override
    public AgendaAgendamentoDTO criarAgendamento(Clinica clinica, NovoAgendamentoRequest dados) {
        Paciente paciente = resolverPacienteParaEscrita(clinica, dados);
        DarwinCreatePatientRequest patientPayload = new DarwinCreatePatientRequest(
                paciente.getNome(), CpfUtils.formatarComMascara(CpfUtils.normalizarDigitos(paciente.getCpf())),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DarwinCreateScheduleRequest request = new DarwinCreateScheduleRequest(
                dados.timetableId(), "Marcado", dados.horarioInicio(), dados.horarioFim(),
                dados.data() + "T00:00:00.000Z", dados.observacao(),
                List.of(new DarwinInsuranceProcedureRef(dados.convenioId(), dados.procedimentoId())),
                null, patientPayload);
        callDarwin(() -> darwinClient.criarAgendamento(request));
        return reconciliarAposEscrita(clinica, paciente, dados.data(), dados.horarioInicio(), dados.horarioFim());
    }

    @Override
    public AgendaAgendamentoDTO criarEncaixe(Clinica clinica, NovoAgendamentoRequest dados) {
        Paciente paciente = resolverPacienteParaEscrita(clinica, dados);
        DarwinCreatePatientRequest patientPayload = new DarwinCreatePatientRequest(
                paciente.getNome(), CpfUtils.formatarComMascara(CpfUtils.normalizarDigitos(paciente.getCpf())),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DarwinCreateFitInScheduleRequest request = new DarwinCreateFitInScheduleRequest(
                dados.profissionalId(), dados.localId(), "Marcado", dados.horarioInicio(), dados.horarioFim(),
                dados.data() + "T00:00:00.000Z", dados.observacao(),
                List.of(new DarwinInsuranceProcedureRef(dados.convenioId(), dados.procedimentoId())),
                null, patientPayload);
        callDarwin(() -> darwinClient.criarAgendamentoEncaixe(request));
        return reconciliarAposEscrita(clinica, paciente, dados.data(), dados.horarioInicio(), dados.horarioFim());
    }

    @Override
    public AgendaAgendamentoDTO atualizarAgendamento(
            Clinica clinica, Long agendamentoLocalId, AtualizarAgendamentoRequest dados) {
        Agendamento existente = agendamentoRepository.findByIdAndClinicaId(agendamentoLocalId, clinica.getId())
                .orElseThrow(() -> new NotFoundException("Agendamento não encontrado"));
        DarwinUpdateScheduleRequest request = new DarwinUpdateScheduleRequest(
                existente.getExternalId(), dados.status(), dados.horarioInicio(), dados.horarioFim(),
                dados.data() == null ? null : dados.data() + "T00:00:00.000Z", dados.timetableId(),
                dados.observacao(),
                dados.procedimentoId() == null && dados.convenioId() == null
                        ? null
                        : List.of(new DarwinInsuranceProcedureRef(dados.convenioId(), dados.procedimentoId())));
        callDarwin(() -> darwinClient.atualizarAgendamento(request));
        if (dados.status() != null) {
            existente.setStatus(dados.status());
        }
        if (dados.data() != null && dados.horarioInicio() != null) {
            existente.setDataHoraInicio(dados.data().atTime(java.time.LocalTime.parse(dados.horarioInicio()))
                    .atZone(java.time.ZoneId.of("America/Sao_Paulo")).toOffsetDateTime());
        }
        if (dados.timetableId() != null) {
            existente.setTimetableId(dados.timetableId());
        }
        existente.setSyncStatus("SYNCED");
        existente.setSyncMensagemErro(null);
        existente.setSincronizadoEm(java.time.OffsetDateTime.now());
        return toDto(agendamentoRepository.save(existente));
    }

    @Override
    public void cancelarAgendamento(Clinica clinica, Long agendamentoLocalId, String motivo) {
        Agendamento existente = agendamentoRepository.findByIdAndClinicaId(agendamentoLocalId, clinica.getId())
                .orElseThrow(() -> new NotFoundException("Agendamento não encontrado"));
        callDarwin(() -> darwinClient.excluirAgendamento(existente.getExternalId()));
        syncService.marcarCancelado(clinica, existente.getExternalId());
    }

    private Paciente resolverPacienteParaEscrita(Clinica clinica, NovoAgendamentoRequest dados) {
        if (dados.pacienteId() != null) {
            return pacienteRepository.findByIdAndClinicaId(dados.pacienteId(), clinica.getId())
                    .orElseThrow(() -> new NotFoundException("Paciente não encontrado"));
        }
        String cpfDigitos = CpfUtils.normalizarDigitos(dados.pacienteCpf());
        if (!CpfUtils.valido(cpfDigitos)) {
            throw new BadRequestException("CPF invalido.");
        }
        return localizarOuCriarPacienteLocal(clinica, cpfDigitos, null, null);
    }

    private AgendaAgendamentoDTO reconciliarAposEscrita(
            Clinica clinica, Paciente paciente, LocalDate data, String horarioInicio, String horarioFim) {
        try {
            DarwinPatientScheduleResponse resposta = darwinConsultaService.listarAgendamentosPorCpf(
                    CpfUtils.formatarComMascara(CpfUtils.normalizarDigitos(paciente.getCpf())), data, data, null);
            Optional<DarwinPatientScheduleResponse.Schedule> correspondente = resposta.schedules() == null
                    ? Optional.empty()
                    : resposta.schedules().stream()
                            .filter(s -> horarioInicio.equals(s.time()))
                            .findFirst();
            if (correspondente.isPresent()) {
                return toDto(syncService.upsertSchedule(clinica, paciente, correspondente.get()));
            }
        } catch (Exception e) {
            log.warn("Falha ao reconciliar agendamento Darwin apos escrita: tipoErro={}", e.getClass().getSimpleName());
        }
        return toDto(syncService.registrarPendenciaReconciliacao(
                clinica, paciente, data, horarioInicio, horarioFim,
                "Escrita confirmada na Darwin; scheduleId ainda nao identificado localmente."));
    }

    private Paciente localizarOuCriarPacienteLocal(
            Clinica clinica, String cpfDigitos, String nomeConhecido, NovoPacienteRequest dadosOpcional) {
        String cpfHash = CpfUtils.hashSeguro(cpfDigitos);
        Optional<Paciente> existente = pacienteRepository.findByClinicaIdAndCpfHash(clinica.getId(), cpfHash);
        if (existente.isPresent()) {
            return existente.get();
        }
        String nome = dadosOpcional != null && dadosOpcional.nome() != null
                ? dadosOpcional.nome()
                : (nomeConhecido != null ? nomeConhecido : "Paciente Darwin");
        Paciente novo = new Paciente();
        novo.setClinica(clinica);
        novo.setNome(nome);
        novo.setNomeBusca(nome.toUpperCase(java.util.Locale.ROOT));
        novo.setCpf(cpfDigitos);
        novo.setCpfHash(cpfHash);
        String telefone = dadosOpcional != null && dadosOpcional.telefone() != null
                ? dadosOpcional.telefone() : "00000000000";
        novo.setTelefone(telefone);
        novo.setTelefoneNormalizado(telefone.replaceAll("[^0-9]", ""));
        novo.setEmail(dadosOpcional == null ? null : dadosOpcional.email());
        novo.setStatus("EM_ATENDIMENTO");
        novo.setChaveCriptografiaId("v1");
        novo.setExternalSource(ExternalProviderType.DARWIN);
        novo.setExternalId(cpfDigitos);
        return pacienteRepository.save(novo);
    }

    private AgendaAgendamentoDTO toDto(Agendamento agendamento) {
        Paciente paciente = agendamento.getPaciente();
        String cpfMascarado = paciente == null
                ? null
                : CpfUtils.mascarar(CpfUtils.normalizarDigitos(paciente.getCpf()));
        return new AgendaAgendamentoDTO(
                agendamento.getId(),
                agendamento.getExternalId(),
                "DARWIN",
                paciente == null ? null : paciente.getId(),
                paciente == null ? null : paciente.getNome(),
                cpfMascarado,
                agendamento.getProfissionalExternoId(),
                agendamento.getProfissionalNome(),
                agendamento.getProcedimentoExternoId(),
                agendamento.getServicoNome(),
                agendamento.getConvenioExternoId(),
                agendamento.getConvenioNome(),
                agendamento.getLocalExternoId(),
                agendamento.getLocalNome(),
                agendamento.getDataHoraInicio() == null ? null : agendamento.getDataHoraInicio().toLocalDate(),
                agendamento.getDataHoraInicio() == null ? null : agendamento.getDataHoraInicio().toLocalTime().toString(),
                agendamento.getDataHoraFim() == null ? null : agendamento.getDataHoraFim().toLocalTime().toString(),
                agendamento.getStatus(),
                agendamento.getTimetableId(),
                null,
                agendamento.getOrigem(),
                agendamento.getSincronizadoEm(),
                agendamento.getSyncStatus());
    }

    private <T> T callDarwin(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (RestClientResponseException e) {
            throw mapStatus(e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new DarwinIntegrationException(504, "Tempo limite ao comunicar com a integracao Darwin.");
        } catch (RestClientException e) {
            throw new DarwinIntegrationException(502, "Resposta invalida da integracao Darwin.");
        }
    }

    private DarwinIntegrationException mapStatus(int status) {
        return switch (status) {
            case 400 -> new DarwinIntegrationException(400, "Parametros invalidos para a integracao Darwin.");
            case 401, 403 -> new DarwinIntegrationException(403, "Acesso negado pelo escopo do token Darwin.");
            case 404 -> new DarwinIntegrationException(404, "Recurso nao encontrado na integracao Darwin.");
            case 409 -> new DarwinIntegrationException(409, "Conflito ao gravar na integracao Darwin.");
            case 429 -> new DarwinIntegrationException(429, "Limite de requisicoes da integracao Darwin excedido.");
            default -> new DarwinIntegrationException(502, "Falha ao comunicar com a integracao Darwin.");
        };
    }
}
