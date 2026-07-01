package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.MensagemFestivaConfig;
import com.synapse.clinicafemina.dto.followup.ConfigStatusRequest;
import com.synapse.clinicafemina.dto.lembrete.MensagemFestivaConfigRequest;
import com.synapse.clinicafemina.dto.lembrete.MensagemFestivaConfigResponse;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.MensagemFestivaConfigRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MensagemFestivaConfigService {

    private final MensagemFestivaConfigRepository repository;

    @Transactional(readOnly = true)
    public List<MensagemFestivaConfigResponse> listar(Clinica clinica) {
        return repository.findByClinicaIdOrderByMesDiaAscNomeAsc(clinica.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MensagemFestivaConfigResponse criar(Clinica clinica, MensagemFestivaConfigRequest request) {
        MensagemFestivaConfig config = new MensagemFestivaConfig();
        config.setClinica(clinica);
        aplicarRequest(config, request);
        return toResponse(repository.save(config));
    }

    @Transactional
    public MensagemFestivaConfigResponse atualizar(Clinica clinica, Long id, MensagemFestivaConfigRequest request) {
        MensagemFestivaConfig config = buscarPorClinica(id, clinica.getId());
        aplicarRequest(config, request);
        return toResponse(repository.save(config));
    }

    @Transactional
    public MensagemFestivaConfigResponse alterarStatus(Clinica clinica, Long id, ConfigStatusRequest request) {
        MensagemFestivaConfig config = buscarPorClinica(id, clinica.getId());
        config.setAtivo(request.ativo());
        return toResponse(repository.save(config));
    }

    private MensagemFestivaConfig buscarPorClinica(Long id, Long clinicaId) {
        return repository.findByIdAndClinicaId(id, clinicaId)
                .orElseThrow(() -> new NotFoundException("Configuração de mensagem festiva não encontrada"));
    }

    private void aplicarRequest(MensagemFestivaConfig config, MensagemFestivaConfigRequest request) {
        config.setChave(AutomacaoValidation.normalizar(request.chave()));
        config.setNome(request.nome());
        config.setMesDia(request.mesDia());
        config.setAtivo(request.ativo() == null ? Boolean.TRUE : request.ativo());
        config.setCanal(AutomacaoValidation.opcaoPadrao(
                request.canal(),
                "WHATSAPP",
                AutomacaoValidation.CANAIS,
                "Canal"
        ));
        config.setMensagemTemplate(request.mensagemTemplate());
        config.setConfigJson(request.configJson());
    }

    private MensagemFestivaConfigResponse toResponse(MensagemFestivaConfig config) {
        return new MensagemFestivaConfigResponse(
                config.getId(),
                config.getClinica().getId(),
                config.getChave(),
                config.getNome(),
                config.getMesDia(),
                config.getAtivo(),
                config.getCanal(),
                config.getMensagemTemplate(),
                config.getConfigJson(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }

}
