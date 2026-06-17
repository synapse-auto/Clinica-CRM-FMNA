package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.ConsultaLembreteConfig;
import com.synapse.clinicafemina.dto.followup.ConfigStatusRequest;
import com.synapse.clinicafemina.dto.lembrete.ConsultaLembreteConfigRequest;
import com.synapse.clinicafemina.dto.lembrete.ConsultaLembreteConfigResponse;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.ConsultaLembreteConfigRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsultaLembreteConfigService {

    private final ConsultaLembreteConfigRepository repository;

    @Transactional(readOnly = true)
    public List<ConsultaLembreteConfigResponse> listar(Clinica clinica) {
        return repository.findByClinicaIdOrderByNomeAsc(clinica.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ConsultaLembreteConfigResponse criar(Clinica clinica, ConsultaLembreteConfigRequest request) {
        ConsultaLembreteConfig config = new ConsultaLembreteConfig();
        config.setClinica(clinica);
        aplicarRequest(config, request);
        return toResponse(repository.save(config));
    }

    @Transactional
    public ConsultaLembreteConfigResponse atualizar(Clinica clinica, Long id, ConsultaLembreteConfigRequest request) {
        ConsultaLembreteConfig config = buscarPorClinica(id, clinica.getId());
        aplicarRequest(config, request);
        return toResponse(repository.save(config));
    }

    @Transactional
    public ConsultaLembreteConfigResponse alterarStatus(Clinica clinica, Long id, ConfigStatusRequest request) {
        ConsultaLembreteConfig config = buscarPorClinica(id, clinica.getId());
        config.setAtivo(request.ativo());
        return toResponse(repository.save(config));
    }

    private ConsultaLembreteConfig buscarPorClinica(Long id, Long clinicaId) {
        return repository.findByIdAndClinicaId(id, clinicaId)
                .orElseThrow(() -> new NotFoundException("Configuração de lembrete de consulta não encontrada"));
    }

    private void aplicarRequest(ConsultaLembreteConfig config, ConsultaLembreteConfigRequest request) {
        config.setNome(request.nome());
        config.setDescricao(request.descricao());
        config.setAtivo(request.ativo() == null ? Boolean.TRUE : request.ativo());
        config.setCanal(normalizarPadrao(request.canal(), "WHATSAPP"));
        config.setAntecedenciaQuantidade(request.antecedenciaQuantidade());
        config.setAntecedenciaUnidade(normalizar(request.antecedenciaUnidade()));
        config.setHorarioEnvio(request.horarioEnvio());
        config.setPermiteConfirmacao(request.permiteConfirmacao() == null ? Boolean.TRUE : request.permiteConfirmacao());
        config.setPermiteCancelamento(request.permiteCancelamento() == null ? Boolean.TRUE : request.permiteCancelamento());
        config.setPermiteReagendamento(request.permiteReagendamento() == null ? Boolean.TRUE : request.permiteReagendamento());
        config.setMensagemTemplate(request.mensagemTemplate());
        config.setConfigJson(request.configJson());
    }

    private ConsultaLembreteConfigResponse toResponse(ConsultaLembreteConfig config) {
        return new ConsultaLembreteConfigResponse(
                config.getId(),
                config.getClinica().getId(),
                config.getNome(),
                config.getDescricao(),
                config.getAtivo(),
                config.getCanal(),
                config.getAntecedenciaQuantidade(),
                config.getAntecedenciaUnidade(),
                config.getHorarioEnvio(),
                config.getPermiteConfirmacao(),
                config.getPermiteCancelamento(),
                config.getPermiteReagendamento(),
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

    private String normalizar(String valor) {
        return valor.trim().toUpperCase(Locale.ROOT);
    }
}
