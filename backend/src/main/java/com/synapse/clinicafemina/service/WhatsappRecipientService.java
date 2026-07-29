package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappPhoneNormalizer;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProvider;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderResolver;
import com.synapse.clinicafemina.integration.whatsapp.model.ResolvedWhatsappRecipient;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappRecipientResolution;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappSendResult;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsappRecipientService {

    private final WhatsappProviderResolver providerResolver;
    private final AtendimentoRepository atendimentoRepository;

    public ResolvedWhatsappRecipient resolve(Atendimento atendimento) {
        WhatsappProvider provider = providerResolver.resolve();
        String registeredPhone = atendimento.getPaciente().getTelefoneNormalizado();
        WhatsappRecipientResolution resolution = provider.resolveRecipient(
                atendimento.getWhatsappChatId(), registeredPhone,
                WhatsappPhoneNormalizer.safeAliases(registeredPhone)
        );
        return new ResolvedWhatsappRecipient(
                provider, resolution.recipient(), resolution.source(), resolution.providerConfirmed()
        );
    }

    public void persistConfirmedRecipient(Atendimento atendimento, WhatsappSendResult result) {
        String confirmed = normalizeConfirmedRecipient(atendimento, result.confirmedRecipient());
        if (confirmed == null || confirmed.equals(atendimento.getWhatsappChatId())) {
            return;
        }
        atendimento.setWhatsappChatId(confirmed);
        atendimentoRepository.save(atendimento);
        log.info("Destinatario WhatsApp confirmado pelo provider. clinicaId={} atendimentoId={} "
                        + "provider={} finalTelefone={}",
                atendimento.getClinica().getId(), atendimento.getId(), result.provider(), maskPhone(confirmed));
    }

    private String normalizeConfirmedRecipient(Atendimento atendimento, String rawRecipient) {
        if (rawRecipient == null || rawRecipient.isBlank()) {
            return null;
        }
        Set<String> aliases = WhatsappPhoneNormalizer.safeAliases(
                atendimento.getPaciente().getTelefoneNormalizado()
        );
        try {
            String normalized = aliases.contains(rawRecipient)
                    ? rawRecipient
                    : WhatsappPhoneNormalizer.normalize(rawRecipient);
            return aliases.contains(normalized) ? normalized : null;
        } catch (BadRequestException exception) {
            return null;
        }
    }

    private String maskPhone(String phone) {
        return phone == null || phone.length() < 4 ? "****" : "******" + phone.substring(phone.length() - 4);
    }
}
