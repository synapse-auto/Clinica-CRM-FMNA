package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class N8nCallbackAuthorizationService {

    public record Autorizacao(Long clinicaId) {
    }

    private final AtendimentoRepository atendimentoRepository;
    private final ClinicaConfigService clinicaConfigService;
    private final String callbackSecret;

    public N8nCallbackAuthorizationService(
            AtendimentoRepository atendimentoRepository,
            ClinicaConfigService clinicaConfigService,
            @Value("${app.n8n.callback-secret:${N8N_CALLBACK_SECRET:}}") String callbackSecret
    ) {
        this.atendimentoRepository = atendimentoRepository;
        this.clinicaConfigService = clinicaConfigService;
        this.callbackSecret = callbackSecret;
    }

    @Transactional(readOnly = true)
    public Autorizacao autorizar(String secret, Long atendimentoId) {
        validarSecret(secret, atendimentoId);
        Clinica clinicaConfigurada = clinicaConfigService.obterClinicaAtual();
        Atendimento atendimento = atendimentoRepository
                .findByIdAndClinicaId(atendimentoId, clinicaConfigurada.getId())
                .orElseThrow(() -> new NotFoundException("Atendimento nao encontrado"));
        Clinica clinica = atendimento.getClinica();
        if (clinica == null || !Boolean.TRUE.equals(clinica.getUsaN8n())) {
            log.warn("Chamada N8N recusada por integracao desabilitada. atendimento={}", atendimentoId);
            throw new AccessDeniedException("Integracao N8N desabilitada.");
        }
        return new Autorizacao(clinica.getId());
    }

    private void validarSecret(String secret, Long atendimentoId) {
        if (callbackSecret == null || callbackSecret.isBlank()) {
            log.warn("Chamada N8N recusada por secret nao configurado. atendimento={}", atendimentoId);
            throw credencialInvalida();
        }
        if (secret == null || secret.isBlank() || !segredoIgual(secret, callbackSecret)) {
            log.warn("Chamada N8N recusada por secret invalido. atendimento={}", atendimentoId);
            throw credencialInvalida();
        }
    }

    private BadCredentialsException credencialInvalida() {
        return new BadCredentialsException("Credencial N8N invalida.");
    }

    private boolean segredoIgual(String recebido, String configurado) {
        byte[] recebidoBytes = recebido.getBytes(StandardCharsets.UTF_8);
        byte[] configuradoBytes = configurado.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(recebidoBytes, configuradoBytes);
    }
}
