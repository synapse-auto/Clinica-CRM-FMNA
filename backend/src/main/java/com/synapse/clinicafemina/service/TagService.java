package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Tag;
import com.synapse.clinicafemina.dto.operacional.StatusRequest;
import com.synapse.clinicafemina.dto.operacional.TagRequest;
import com.synapse.clinicafemina.dto.operacional.TagResponse;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.TagRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TagService {

    private static final String DEFAULT_COLOR = "#0d9488";

    private final TagRepository repository;

    @Transactional(readOnly = true)
    public List<TagResponse> listar(Clinica clinica) {
        return repository.findByClinicaIdAndDeletadoEmIsNullOrderByNomeAsc(clinica.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TagResponse criar(Clinica clinica, TagRequest request) {
        String nome = required(request.nome(), "Nome da tag é obrigatório.");
        validarNomeDisponivel(clinica.getId(), nome, null);
        Tag tag = new Tag();
        tag.setClinica(clinica);
        aplicarRequest(tag, request, nome);
        return toResponse(repository.save(tag));
    }

    @Transactional
    public TagResponse atualizar(Clinica clinica, Long id, TagRequest request) {
        Tag tag = buscarPorClinica(clinica, id);
        String nome = required(request.nome(), "Nome da tag é obrigatório.");
        validarNomeDisponivel(clinica.getId(), nome, id);
        aplicarRequest(tag, request, nome);
        return toResponse(repository.save(tag));
    }

    @Transactional
    public TagResponse alterarStatus(Clinica clinica, Long id, StatusRequest request) {
        Tag tag = buscarPorClinica(clinica, id);
        tag.setAtivo(request.ativo());
        return toResponse(repository.save(tag));
    }

    @Transactional
    public void excluir(Clinica clinica, Long id) {
        Tag tag = buscarPorClinica(clinica, id);
        tag.setAtivo(false);
        tag.setDeletadoEm(OffsetDateTime.now());
    }

    private Tag buscarPorClinica(Clinica clinica, Long id) {
        return repository.findByIdAndClinicaIdAndDeletadoEmIsNull(id, clinica.getId())
                .orElseThrow(() -> new NotFoundException("Tag não encontrada"));
    }

    private void aplicarRequest(Tag tag, TagRequest request, String nome) {
        tag.setNome(nome);
        tag.setCor(normalizeColor(request.cor()));
        tag.setDescricao(blankToNull(request.descricao()));
        tag.setAtivo(request.ativo() == null ? Boolean.TRUE : request.ativo());
    }

    private void validarNomeDisponivel(Long clinicaId, String nome, Long ignoreId) {
        boolean exists = ignoreId == null
                ? repository.existsByClinicaIdAndNomeIgnoreCaseAndDeletadoEmIsNull(clinicaId, nome)
                : repository.existsByClinicaIdAndNomeIgnoreCaseAndDeletadoEmIsNullAndIdNot(clinicaId, nome, ignoreId);
        if (exists) {
            throw new BadRequestException("Já existe uma tag ativa com este nome.");
        }
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

    private String normalizeColor(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_COLOR;
        }
        return value.trim();
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
