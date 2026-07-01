package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.AtendimentoTag;
import com.synapse.clinicafemina.domain.AtendimentoTagId;
import com.synapse.clinicafemina.domain.Tag;
import com.synapse.clinicafemina.dto.operacional.TagResponse;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.AtendimentoTagRepository;
import com.synapse.clinicafemina.repository.TagRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AtendimentoTagService {

    private final AtendimentoRepository atendimentoRepository;
    private final TagRepository tagRepository;
    private final AtendimentoTagRepository atendimentoTagRepository;

    @Transactional(readOnly = true)
    public List<TagResponse> listar(Long atendimentoId, Long clinicaId) {
        buscarAtendimento(atendimentoId, clinicaId);
        return atendimentoTagRepository.findTagsByAtendimentoIdAndClinicaId(atendimentoId, clinicaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<TagResponse> adicionar(Long atendimentoId, Long tagId, Long clinicaId) {
        Atendimento atendimento = buscarAtendimento(atendimentoId, clinicaId);
        Tag tag = buscarTagAtiva(tagId, clinicaId);
        AtendimentoTagId id = new AtendimentoTagId(atendimentoId, tagId);

        if (!atendimentoTagRepository.existsById(id)) {
            atendimentoTagRepository.save(new AtendimentoTag(atendimento, tag));
        }

        return atendimentoTagRepository.findTagsByAtendimentoIdAndClinicaId(atendimentoId, clinicaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void remover(Long atendimentoId, Long tagId, Long clinicaId) {
        buscarAtendimento(atendimentoId, clinicaId);
        atendimentoTagRepository.deleteByAtendimentoIdAndTagIdAndClinicaId(atendimentoId, tagId, clinicaId);
    }

    private Atendimento buscarAtendimento(Long atendimentoId, Long clinicaId) {
        return atendimentoRepository.findByIdAndClinicaId(atendimentoId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Atendimento não encontrado"));
    }

    private Tag buscarTagAtiva(Long tagId, Long clinicaId) {
        Tag tag = tagRepository.findByIdAndClinicaIdAndDeletadoEmIsNull(tagId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Tag não encontrada"));
        if (!Boolean.TRUE.equals(tag.getAtivo())) {
            throw new BadRequestException("Tag inativa não pode ser vinculada ao atendimento.");
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
