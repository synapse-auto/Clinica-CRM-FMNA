package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.CategoriaMensagemRapida;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.MensagemRapida;
import com.synapse.clinicafemina.dto.operacional.CategoriaMensagemRapidaResponse;
import com.synapse.clinicafemina.dto.operacional.MensagemRapidaRequest;
import com.synapse.clinicafemina.dto.operacional.MensagemRapidaResponse;
import com.synapse.clinicafemina.dto.operacional.StatusRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.CategoriaMensagemRapidaRepository;
import com.synapse.clinicafemina.repository.MensagemRapidaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MensagemRapidaService {

    private final MensagemRapidaRepository repository;
    private final CategoriaMensagemRapidaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public List<MensagemRapidaResponse> listar(Clinica clinica) {
        return repository.findByClinicaIdAndDeletadoEmIsNullOrderByTituloAsc(clinica.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoriaMensagemRapidaResponse> listarCategorias() {
        return categoriaRepository.findAllByOrderByRotuloAsc()
                .stream()
                .map(this::toCategoriaResponse)
                .toList();
    }

    @Transactional
    public MensagemRapidaResponse criar(Clinica clinica, MensagemRapidaRequest request) {
        String atalho = normalizeShortcut(request.atalho());
        validarAtalhoDisponivel(clinica.getId(), atalho, null);
        MensagemRapida mensagem = new MensagemRapida();
        mensagem.setClinica(clinica);
        aplicarRequest(mensagem, request, atalho);
        return toResponse(repository.save(mensagem));
    }

    @Transactional
    public MensagemRapidaResponse atualizar(Clinica clinica, Long id, MensagemRapidaRequest request) {
        MensagemRapida mensagem = buscarPorClinica(clinica, id);
        String atalho = normalizeShortcut(request.atalho());
        validarAtalhoDisponivel(clinica.getId(), atalho, id);
        aplicarRequest(mensagem, request, atalho);
        return toResponse(repository.save(mensagem));
    }

    @Transactional
    public MensagemRapidaResponse alterarStatus(Clinica clinica, Long id, StatusRequest request) {
        MensagemRapida mensagem = buscarPorClinica(clinica, id);
        mensagem.setAtivo(request.ativo());
        return toResponse(repository.save(mensagem));
    }

    @Transactional
    public void excluir(Clinica clinica, Long id) {
        MensagemRapida mensagem = buscarPorClinica(clinica, id);
        mensagem.setAtivo(false);
        mensagem.setDeletadoEm(OffsetDateTime.now());
    }

    private MensagemRapida buscarPorClinica(Clinica clinica, Long id) {
        return repository.findByIdAndClinicaIdAndDeletadoEmIsNull(id, clinica.getId())
                .orElseThrow(() -> new NotFoundException("Mensagem rápida não encontrada"));
    }

    private void aplicarRequest(MensagemRapida mensagem, MensagemRapidaRequest request, String atalho) {
        mensagem.setCategoria(buscarCategoria(request.categoriaId()));
        mensagem.setTitulo(required(request.titulo(), "Título é obrigatório."));
        mensagem.setAtalho(atalho);
        mensagem.setConteudo(required(request.conteudo(), "Conteúdo é obrigatório."));
        mensagem.setAtivo(request.ativo() == null ? Boolean.TRUE : request.ativo());
    }

    private CategoriaMensagemRapida buscarCategoria(Short categoriaId) {
        if (categoriaId == null) {
            return null;
        }
        return categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new BadRequestException("Categoria de mensagem rápida inválida."));
    }

    private void validarAtalhoDisponivel(Long clinicaId, String atalho, Long ignoreId) {
        boolean exists = ignoreId == null
                ? repository.existsByClinicaIdAndAtalhoIgnoreCaseAndDeletadoEmIsNull(clinicaId, atalho)
                : repository.existsByClinicaIdAndAtalhoIgnoreCaseAndDeletadoEmIsNullAndIdNot(clinicaId, atalho, ignoreId);
        if (exists) {
            throw new BadRequestException("Já existe uma mensagem rápida ativa com este atalho.");
        }
    }

    private MensagemRapidaResponse toResponse(MensagemRapida mensagem) {
        CategoriaMensagemRapida categoria = mensagem.getCategoria();
        return new MensagemRapidaResponse(
                mensagem.getId(),
                categoria == null ? null : categoria.getId(),
                categoria == null ? null : categoria.getRotulo(),
                categoria == null ? null : categoria.getCor(),
                mensagem.getTitulo(),
                mensagem.getAtalho(),
                mensagem.getConteudo(),
                Boolean.TRUE.equals(mensagem.getAtivo()),
                mensagem.getCriadoEm(),
                mensagem.getAtualizadoEm()
        );
    }

    private CategoriaMensagemRapidaResponse toCategoriaResponse(CategoriaMensagemRapida categoria) {
        return new CategoriaMensagemRapidaResponse(
                categoria.getId(),
                categoria.getCodigo(),
                categoria.getRotulo(),
                categoria.getCor()
        );
    }

    private String normalizeShortcut(String value) {
        String normalized = required(value, "Atalho é obrigatório.").toLowerCase();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (!normalized.matches("^/[a-z0-9_-]{2,39}$")) {
            throw new BadRequestException("Atalho deve começar com / e conter letras, números, _ ou -.");
        }
        return normalized;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }
}
