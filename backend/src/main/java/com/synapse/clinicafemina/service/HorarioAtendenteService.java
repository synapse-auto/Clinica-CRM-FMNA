package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.HorarioAtendente;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.operacional.HorarioAtendenteRequest;
import com.synapse.clinicafemina.dto.operacional.HorarioAtendenteResponse;
import com.synapse.clinicafemina.dto.operacional.StatusRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.HorarioAtendenteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HorarioAtendenteService {

    private final HorarioAtendenteRepository repository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<HorarioAtendenteResponse> listar(Clinica clinica) {
        return repository.findAtivosByClinicaId(clinica.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public HorarioAtendenteResponse criar(Clinica clinica, HorarioAtendenteRequest request) {
        validarIntervalo(request);
        Usuario usuario = buscarUsuario(clinica, request.usuarioId());
        HorarioAtendente horario = new HorarioAtendente();
        horario.setUsuario(usuario);
        aplicarRequest(horario, request);
        return toResponse(repository.save(horario));
    }

    @Transactional
    public HorarioAtendenteResponse atualizar(Clinica clinica, Long id, HorarioAtendenteRequest request) {
        validarIntervalo(request);
        HorarioAtendente horario = buscarPorClinica(clinica, id);
        horario.setUsuario(buscarUsuario(clinica, request.usuarioId()));
        aplicarRequest(horario, request);
        return toResponse(repository.save(horario));
    }

    @Transactional
    public HorarioAtendenteResponse alterarStatus(Clinica clinica, Long id, StatusRequest request) {
        HorarioAtendente horario = buscarPorClinica(clinica, id);
        horario.setAtivo(request.ativo());
        return toResponse(repository.save(horario));
    }

    @Transactional
    public void excluir(Clinica clinica, Long id) {
        HorarioAtendente horario = buscarPorClinica(clinica, id);
        horario.setAtivo(false);
        horario.setDeletadoEm(OffsetDateTime.now());
    }

    private HorarioAtendente buscarPorClinica(Clinica clinica, Long id) {
        return repository.findByIdAndUsuarioClinicaIdAndDeletadoEmIsNull(id, clinica.getId())
                .orElseThrow(() -> new NotFoundException("Horário não encontrado"));
    }

    private Usuario buscarUsuario(Clinica clinica, Long usuarioId) {
        return usuarioRepository.findAtivoByIdAndClinicaId(usuarioId, clinica.getId())
                .orElseThrow(() -> new BadRequestException("Atendente inválido para esta clínica."));
    }

    private void aplicarRequest(HorarioAtendente horario, HorarioAtendenteRequest request) {
        horario.setDiaSemana(request.diaSemana());
        horario.setHoraInicio(request.horaInicio());
        horario.setHoraFim(request.horaFim());
        horario.setAtivo(request.ativo() == null ? Boolean.TRUE : request.ativo());
    }

    private void validarIntervalo(HorarioAtendenteRequest request) {
        if (request.horaInicio() == null || request.horaFim() == null) {
            throw new BadRequestException("Hora início e hora fim são obrigatórias.");
        }
        if (!request.horaInicio().isBefore(request.horaFim())) {
            throw new BadRequestException("Hora início deve ser anterior à hora fim.");
        }
    }

    private HorarioAtendenteResponse toResponse(HorarioAtendente horario) {
        Usuario usuario = horario.getUsuario();
        return new HorarioAtendenteResponse(
                horario.getId(),
                usuario.getId(),
                usuario.getNome(),
                horario.getDiaSemana(),
                horario.getHoraInicio(),
                horario.getHoraFim(),
                Boolean.TRUE.equals(horario.getAtivo()),
                horario.getCriadoEm(),
                horario.getAtualizadoEm()
        );
    }
}
