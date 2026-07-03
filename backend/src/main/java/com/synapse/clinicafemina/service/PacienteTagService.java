package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.PacienteTag;
import com.synapse.clinicafemina.domain.PacienteTagId;
import com.synapse.clinicafemina.domain.Tag;
import com.synapse.clinicafemina.dto.operacional.TagResponse;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.PacienteTagRepository;
import com.synapse.clinicafemina.repository.TagRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PacienteTagService {

    private final PacienteRepository pacienteRepository;
    private final TagRepository tagRepository;
    private final PacienteTagRepository pacienteTagRepository;

    @Transactional(readOnly = true)
    public List<TagResponse> listar(Long pacienteId, Long clinicaId) {
        buscarPaciente(pacienteId, clinicaId);
        return pacienteTagRepository.findTagsByPacienteIdAndClinicaId(pacienteId, clinicaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<TagResponse> adicionar(Long pacienteId, Long tagId, Long clinicaId) {
        Paciente paciente = buscarPaciente(pacienteId, clinicaId);
        Tag tag = buscarTagAtiva(tagId, clinicaId);
        PacienteTagId id = new PacienteTagId(pacienteId, tagId);

        if (!pacienteTagRepository.existsById(id)) {
            pacienteTagRepository.save(new PacienteTag(paciente, tag));
        }

        return pacienteTagRepository.findTagsByPacienteIdAndClinicaId(pacienteId, clinicaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void remover(Long pacienteId, Long tagId, Long clinicaId) {
        buscarPaciente(pacienteId, clinicaId);
        pacienteTagRepository.deleteByPacienteIdAndTagIdAndClinicaId(pacienteId, tagId, clinicaId);
    }

    private Paciente buscarPaciente(Long pacienteId, Long clinicaId) {
        return pacienteRepository.findByIdAndClinicaId(pacienteId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Paciente nao encontrado"));
    }

    private Tag buscarTagAtiva(Long tagId, Long clinicaId) {
        Tag tag = tagRepository.findByIdAndClinicaIdAndDeletadoEmIsNull(tagId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Tag nao encontrada"));
        if (!Boolean.TRUE.equals(tag.getAtivo())) {
            throw new BadRequestException("Tag inativa nao pode ser vinculada ao paciente.");
        }
        return tag;
    }

    private TagResponse toResponse(Tag tag) {
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
}
