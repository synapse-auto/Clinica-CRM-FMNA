package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regra de negócio central do enriquecimento de foto de perfil via UAZAP: decide se vale a pena
 * consultar a UAZAP e se o resultado pode ser persistido com segurança.
 *
 * <p>Reutilizado por dois chamadores: {@link UazapPictureEnrichmentEventListener} (assíncrono,
 * disparado após o commit do webhook) e o endpoint administrativo de diagnóstico — garantindo que
 * ambos executem exatamente a mesma lógica de decisão/persistência.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UazapProfilePhotoEnrichmentService {

    private final PacienteRepository pacienteRepository;
    private final WhatsappProperties whatsappProperties;
    private final UazapProfilePhotoClient photoClient;
    private final UazapPicturePayloadParser payloadParser;

    @Transactional
    public UazapPictureEnrichmentOutcome enriquecer(Long pacienteId) {
        if (whatsappProperties.resolveProvider() != WhatsappProviderType.UAZAP) {
            return UazapPictureEnrichmentOutcome.semTentativa("PROVIDER_ATIVO_NAO_E_UAZAP");
        }
        Paciente paciente = pacienteRepository.findById(pacienteId).orElse(null);
        if (paciente == null) {
            return UazapPictureEnrichmentOutcome.semTentativa("PACIENTE_NAO_ENCONTRADO");
        }
        if (paciente.getFotoUrl() != null && !paciente.getFotoUrl().isBlank()) {
            return UazapPictureEnrichmentOutcome.semTentativa("PACIENTE_JA_POSSUI_FOTO");
        }
        String telefone = paciente.getTelefoneNormalizado();
        if (telefone == null || telefone.isBlank()) {
            return UazapPictureEnrichmentOutcome.semTentativa("TELEFONE_INDISPONIVEL");
        }

        UazapPictureRawResponse raw;
        try {
            raw = photoClient.buscarFotoPerfil(telefone);
        } catch (Exception exception) {
            log.warn("Falha ao consultar foto de perfil UAZAP; paciente permanece sem foto. tipoErro={}",
                    exception.getClass().getSimpleName());
            return UazapPictureEnrichmentOutcome.semTentativa("FALHA_DE_COMUNICACAO_COM_UAZAP");
        }

        UazapPictureEnrichmentOutcome outcome = payloadParser.parse(raw);
        if (outcome.fotoUrl() == null) {
            log.debug("Foto de perfil UAZAP não persistida. formato={}, motivo={}", outcome.formato(), outcome.motivoNaoPersistida());
            return outcome;
        }

        paciente.setFotoUrl(outcome.fotoUrl());
        pacienteRepository.save(paciente);
        log.info("Foto de perfil UAZAP enriquecida com sucesso.");
        return outcome.comFotoPersistida();
    }
}
