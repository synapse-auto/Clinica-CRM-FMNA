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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PacienteService {

    private final PacienteRepository pacienteRepository;
    private final PacienteTagRepository pacienteTagRepository;

    /**
     * Retorna pacientes ativos da clínica usando apenas colunas não criptografadas.
     * Isso evita que dados legados em campos cifrados quebrem a listagem.
     */
    @Transactional(readOnly = true)
    public List<PacienteResumoDTO> listar(Clinica clinica) {
        return pacienteRepository.findResumosDisponiveisByClinicaId(clinica.getId())
                .stream()
                .map(this::toResumo)
                .toList();
    }

    /**
     * Busca um resumo seguro do paciente por ID dentro da clínica.
     */
    @Transactional(readOnly = true)
    public PacienteResumoDTO buscarPorId(Long id, Clinica clinica) {
        return pacienteRepository.findResumoByIdAndClinicaId(id, clinica.getId())
                .map(this::toResumo)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado"));
    }

    private PacienteResumoDTO toResumo(PacienteRepository.PacienteResumoProjection paciente) {
        Long clinicaId = paciente.getClinicaId();
        return new PacienteResumoDTO(
                paciente.getId(),
                paciente.getNomeBusca(),
                paciente.getTelefoneNormalizado(),
                paciente.getStatus(),
                paciente.getExternalSource(),
                paciente.getExternalId(),
                toOffsetDateTime(paciente.getCriadoEm()),
                toOffsetDateTime(paciente.getUltimaInteracaoEm()),
                tagsDoPaciente(paciente.getId(), clinicaId)
        );
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
