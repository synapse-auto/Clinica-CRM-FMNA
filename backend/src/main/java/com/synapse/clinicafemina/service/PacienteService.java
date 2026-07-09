package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Tag;
import com.synapse.clinicafemina.dto.operacional.TagResponse;
import com.synapse.clinicafemina.dto.paciente.PacienteResumoDTO;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.PacienteTagRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PacienteService {

    private final PacienteRepository pacienteRepository;
    private final PacienteTagRepository pacienteTagRepository;

    /**
     * Retorna pacientes ativos da clinica usando apenas colunas nao criptografadas.
     * Isso evita que dados legados em campos cifrados quebrem a listagem.
     */
    @Transactional(readOnly = true)
    public List<PacienteResumoDTO> listar(Clinica clinica) {
        List<PacienteRepository.PacienteResumoProjection> pacientes =
                pacienteRepository.findResumosDisponiveisByClinicaId(clinica.getId());
        Map<Long, List<TagResponse>> tagsPorPaciente = tagsDosPacientes(pacientes, clinica.getId());
        return pacientes.stream()
                .map(paciente -> toResumo(paciente, tagsPorPaciente))
                .toList();
    }

    /**
     * Busca um resumo seguro do paciente por ID dentro da clinica.
     */
    @Transactional(readOnly = true)
    public PacienteResumoDTO buscarPorId(Long id, Clinica clinica) {
        return pacienteRepository.findResumoByIdAndClinicaId(id, clinica.getId())
                .map(paciente -> toResumo(
                        paciente,
                        Map.of(paciente.getId(), tagsDoPaciente(paciente.getId(), clinica.getId()))
                ))
                .orElseThrow(() -> new NotFoundException("Paciente nao encontrado"));
    }

    private PacienteResumoDTO toResumo(
            PacienteRepository.PacienteResumoProjection paciente,
            Map<Long, List<TagResponse>> tagsPorPaciente
    ) {
        return new PacienteResumoDTO(
                paciente.getId(),
                paciente.getNomeBusca(),
                paciente.getTelefoneNormalizado(),
                paciente.getStatus(),
                paciente.getExternalSource(),
                paciente.getExternalId(),
                paciente.getFotoUrl(),
                toOffsetDateTime(paciente.getCriadoEm()),
                toOffsetDateTime(paciente.getUltimaInteracaoEm()),
                tagsPorPaciente.getOrDefault(paciente.getId(), List.of())
        );
    }

    private Map<Long, List<TagResponse>> tagsDosPacientes(
            List<PacienteRepository.PacienteResumoProjection> pacientes,
            Long clinicaId
    ) {
        if (clinicaId == null || pacientes.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = pacientes.stream()
                .map(PacienteRepository.PacienteResumoProjection::getId)
                .toList();
        Map<Long, List<TagResponse>> tagsPorPaciente = new HashMap<>();
        for (Object[] linha : pacienteTagRepository.findTagsByPacienteIdsAndClinicaId(ids, clinicaId)) {
            Long pacienteId = (Long) linha[0];
            Tag tag = (Tag) linha[1];
            tagsPorPaciente
                    .computeIfAbsent(pacienteId, ignored -> new ArrayList<>())
                    .add(toTagResponse(tag));
        }
        return tagsPorPaciente;
    }

    private List<TagResponse> tagsDoPaciente(Long pacienteId, Long clinicaId) {
        if (clinicaId == null) {
            return List.of();
        }
        List<Tag> tags = pacienteTagRepository.findTagsByPacienteIdAndClinicaId(pacienteId, clinicaId);
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .map(this::toTagResponse)
                .toList();
    }

    private TagResponse toTagResponse(Tag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getNome(),
                tag.getCor(),
                tag.getDescricao(),
                Boolean.TRUE.equals(tag.getAtivo()),
                tag.getCriadoEm(),
                tag.getAtualizadoEm()
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
