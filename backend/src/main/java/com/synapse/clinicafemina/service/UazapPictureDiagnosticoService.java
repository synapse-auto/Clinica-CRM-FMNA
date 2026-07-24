package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.uazap.UazapPictureDiagnosticoResponse;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.integration.whatsapp.uazap.UazapPictureEnrichmentOutcome;
import com.synapse.clinicafemina.integration.whatsapp.uazap.UazapProfilePhotoEnrichmentService;
import com.synapse.clinicafemina.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra o diagnóstico administrativo: garante que o paciente pertence à clínica do admin
 * autenticado, então delega a mesma lógica de produção ({@link UazapProfilePhotoEnrichmentService})
 * usada pelo enriquecimento automático do webhook.
 */
@Service
@RequiredArgsConstructor
public class UazapPictureDiagnosticoService {

    private final PacienteRepository pacienteRepository;
    private final UazapProfilePhotoEnrichmentService enrichmentService;

    @Transactional
    public UazapPictureDiagnosticoResponse diagnosticar(Clinica clinica, Long pacienteId) {
        Paciente paciente = pacienteRepository.findByIdAndClinicaId(pacienteId, clinica.getId())
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado"));
        UazapPictureEnrichmentOutcome outcome = enrichmentService.enriquecer(paciente.getId());
        return UazapPictureDiagnosticoResponse.from(outcome);
    }
}
