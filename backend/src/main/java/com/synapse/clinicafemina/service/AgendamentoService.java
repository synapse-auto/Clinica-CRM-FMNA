package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Medico;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.agendamento.AgendaOptionResponse;
import com.synapse.clinicafemina.dto.agendamento.AgendaOptionsResponse;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoCancelRequest;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoCreateRequest;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoResponse;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoUpdateRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgendamentoService {

    private static final String STATUS_AGENDADO = "AGENDADO";
    private static final String STATUS_CANCELADO = "CANCELADO";
    private static final String ORIGEM_MANUAL = "MANUAL";

    private final AgendamentoRepository agendamentoRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listar(
            Clinica clinica,
            OffsetDateTime inicio,
            OffsetDateTime fim
    ) {
        validarPeriodo(inicio, fim);
        return agendamentoRepository
                .findByClinicaIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                        clinica.getId(), inicio, fim)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgendaOptionsResponse listarOpcoes(Clinica clinica) {
        List<AgendaOptionResponse> pacientes = pacienteRepository
                .findDisponiveisByClinicaId(clinica.getId())
                .stream()
                .map(paciente -> new AgendaOptionResponse(paciente.getId(), paciente.getNome()))
                .toList();
        List<AgendaOptionResponse> medicos = usuarioRepository
                .findMedicosAtivosByClinicaId(clinica.getId())
                .stream()
                .map(medico -> new AgendaOptionResponse(medico.getId(), medico.getNome()))
                .toList();
        return new AgendaOptionsResponse(pacientes, medicos);
    }

    @Transactional
    public AgendamentoResponse criar(Clinica clinica, AgendamentoCreateRequest request) {
        validarHorario(request.dataHoraInicio(), request.dataHoraFim());
        Agendamento agendamento = new Agendamento();
        agendamento.setClinica(clinica);
        agendamento.setExternalSource(ExternalProviderType.MANUAL);
        agendamento.setExternalId("crm-" + UUID.randomUUID());
        agendamento.setStatus(STATUS_AGENDADO);
        agendamento.setOrigem(ORIGEM_MANUAL);
        agendamento.setConfirmacaoEnviada(0);
        aplicarDados(agendamento, clinica, request.pacienteId(), request.medicoId(),
                request.dataHoraInicio(), request.dataHoraFim(), request.tipo(), request.servicoNome());
        return toResponse(agendamentoRepository.save(agendamento));
    }

    @Transactional
    public AgendamentoResponse atualizar(
            Clinica clinica,
            Long id,
            AgendamentoUpdateRequest request
    ) {
        validarHorario(request.dataHoraInicio(), request.dataHoraFim());
        Agendamento agendamento = buscarPorClinica(id, clinica.getId());
        if (STATUS_CANCELADO.equals(agendamento.getStatus())) {
            throw new IllegalStateException("Agendamento cancelado não pode ser alterado");
        }
        aplicarDados(agendamento, clinica, request.pacienteId(), request.medicoId(),
                request.dataHoraInicio(), request.dataHoraFim(), request.tipo(), request.servicoNome());
        return toResponse(agendamentoRepository.save(agendamento));
    }

    @Transactional
    public AgendamentoResponse cancelar(
            Clinica clinica,
            Long id,
            AgendamentoCancelRequest request
    ) {
        Agendamento agendamento = buscarPorClinica(id, clinica.getId());
        if (!STATUS_CANCELADO.equals(agendamento.getStatus())) {
            agendamento.setStatus(STATUS_CANCELADO);
            agendamento.setCanceladoEm(OffsetDateTime.now());
            agendamento.setMotivoCancelamento(request.motivo().trim());
            agendamento = agendamentoRepository.save(agendamento);
        }
        return toResponse(agendamento);
    }

    private void aplicarDados(
            Agendamento agendamento,
            Clinica clinica,
            Long pacienteId,
            Long medicoId,
            OffsetDateTime inicio,
            OffsetDateTime fim,
            String tipo,
            String servicoNome
    ) {
        agendamento.setPaciente(buscarPaciente(pacienteId, clinica.getId()));
        agendamento.setMedico(buscarMedico(medicoId, clinica.getId()));
        agendamento.setDataHoraInicio(inicio);
        agendamento.setDataHoraFim(fim);
        agendamento.setTipo(tipo.trim().toUpperCase(Locale.ROOT));
        agendamento.setServicoNome(servicoNome.trim());
    }

    private Paciente buscarPaciente(Long pacienteId, Long clinicaId) {
        Paciente paciente = pacienteRepository.findByIdAndClinicaId(pacienteId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado"));
        if (paciente.getDeletadoEm() != null) {
            throw new NotFoundException("Paciente não encontrado");
        }
        return paciente;
    }

    private Usuario buscarMedico(Long medicoId, Long clinicaId) {
        if (medicoId == null) {
            return null;
        }
        Usuario usuario = usuarioRepository.findAtivoByIdAndClinicaId(medicoId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Médico não encontrado"));
        if (!(usuario instanceof Medico) && !"MEDICO".equals(usuario.getPerfil())) {
            throw new NotFoundException("Médico não encontrado");
        }
        return usuario;
    }

    private Agendamento buscarPorClinica(Long id, Long clinicaId) {
        return agendamentoRepository.findByIdAndClinicaId(id, clinicaId)
                .orElseThrow(() -> new NotFoundException("Agendamento não encontrado"));
    }

    private void validarPeriodo(OffsetDateTime inicio, OffsetDateTime fim) {
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            throw new BadRequestException("Período de consulta inválido");
        }
    }

    private void validarHorario(OffsetDateTime inicio, OffsetDateTime fim) {
        if (inicio == null) {
            throw new BadRequestException("Data e hora de início são obrigatórias");
        }
        if (fim != null && !fim.isAfter(inicio)) {
            throw new BadRequestException("Horário final deve ser posterior ao inicial");
        }
    }

    private AgendamentoResponse toResponse(Agendamento agendamento) {
        Usuario medico = agendamento.getMedico();
        return new AgendamentoResponse(
                agendamento.getId(),
                agendamento.getPaciente().getId(),
                agendamento.getPaciente().getNome(),
                medico == null ? null : medico.getId(),
                medico == null ? null : medico.getNome(),
                agendamento.getDataHoraInicio(),
                agendamento.getDataHoraFim(),
                agendamento.getTipo(),
                agendamento.getServicoNome(),
                agendamento.getStatus(),
                agendamento.getOrigem(),
                agendamento.getConfirmacaoEnviada(),
                agendamento.getCanceladoEm(),
                agendamento.getMotivoCancelamento()
        );
    }
}
