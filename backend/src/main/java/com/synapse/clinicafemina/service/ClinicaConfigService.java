package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClinicaConfigService {

    private final ClinicaRepository clinicaRepository;

    @Value("${app.clinic.slug:}")
    private String clinicSlug;

    @Transactional(readOnly = true)
    public Clinica obterClinicaAtual() {
        if (clinicSlug == null || clinicSlug.isBlank()) {
            throw new IllegalStateException("CLINIC_SLUG deve ser configurado para fluxos internos");
        }
        return clinicaRepository.findBySlug(clinicSlug)
                .orElseThrow(() -> new NotFoundException("Clínica atual não encontrada"));
    }

    @Transactional(readOnly = true)
    public Optional<Clinica> obterPorWhatsappPhoneNumberId(String phoneNumberId) {
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            return Optional.empty();
        }
        return clinicaRepository.findByWhatsappPhoneNumberId(phoneNumberId);
    }
}
