package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.FollowUpConfig;
import com.synapse.clinicafemina.dto.followup.ConfigStatusRequest;
import com.synapse.clinicafemina.dto.followup.FollowUpConfigRequest;
import com.synapse.clinicafemina.dto.followup.FollowUpConfigResponse;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.FollowUpConfigRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowUpConfigService {

    private final FollowUpConfigRepository repository;

    @Transactional(readOnly = true)
    public List<FollowUpConfigResponse> listar(Clinica clinica) {
        return repository.findByClinicaIdOrderByNomeAsc(clinica.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FollowUpConfigResponse criar(Clinica clinica, FollowUpConfigRequest request) {
        FollowUpConfig config = new FollowUpConfig();
        config.setClinica(clinica);
        aplicarRequest(config, request);
        return toResponse(repository.save(config));
    }

    @Transactional
    public FollowUpConfigResponse atualizar(Clinica clinica, Long id, FollowUpConfigRequest request) {
        FollowUpConfig config = buscarPorClinica(id, clinica.getId());
        aplicarRequest(config, request);
        return toResponse(repository.save(config));
    }

    @Transactional
    public FollowUpConfigResponse alterarStatus(Clinica clinica, Long id, ConfigStatusRequest request) {
        FollowUpConfig config = buscarPorClinica(id, clinica.getId());
        config.setAtivo(request.ativo());
        return toResponse(repository.save(config));
    }

    private FollowUpConfig buscarPorClinica(Long id, Long clinicaId) {
        return repository.findByIdAndClinicaId(id, clinicaId)
                .orElseThrow(() -> new NotFoundException("Configuração de follow-up não encontrada"));
    }

    private void aplicarRequest(FollowUpConfig config, FollowUpConfigRequest request) {
        config.setNome(request.nome());
        config.setDescricao(request.descricao());
        config.setAtivo(request.ativo() == null ? Boolean.TRUE : request.ativo());
        config.setGatilho(normalizar(request.gatilho()));
        config.setCanal(normalizarPadrao(request.canal(), "WHATSAPP"));
        config.setDelayQuantidade(request.delayQuantidade());
        config.setDelayUnidade(normalizarOpcional(request.delayUnidade()));
        config.setHorarioEnvio(request.horarioEnvio());
        config.setMensagemTemplate(request.mensagemTemplate());
        config.setConfigJson(request.configJson());
    }

    private FollowUpConfigResponse toResponse(FollowUpConfig config) {
        return new FollowUpConfigResponse(
                config.getId(),
                config.getClinica().getId(),
                config.getNome(),
                config.getDescricao(),
                config.getAtivo(),
                config.getGatilho(),
                config.getCanal(),
                config.getDelayQuantidade(),
                config.getDelayUnidade(),
                config.getHorarioEnvio(),
                config.getMensagemTemplate(),
                config.getConfigJson(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }

    private String normalizarPadrao(String valor, String padrao) {
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        return normalizar(valor);
    }

    private String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return normalizar(valor);
    }

    private String normalizar(String valor) {
        return valor.trim().toUpperCase(Locale.ROOT);
    }
}
