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

    public UazapPictureDiagnosticoResponse diagnosticar(Clinica clinica, Long pacienteId) {
        Paciente paciente = pacienteRepository.findByIdAndClinicaId(pacienteId, clinica.getId())
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado"));
        UazapPictureEnrichmentOutcome outcome =
                enrichmentService.enriquecer(paciente.getId(), clinica.getId(), true);
        return UazapPictureDiagnosticoResponse.from(outcome);
    }

    public void reprocessar(Clinica clinica, Long pacienteId) {
        Paciente paciente = pacienteRepository.findByIdAndClinicaId(pacienteId, clinica.getId())
                .orElseThrow(() -> new NotFoundException("Paciente nao encontrado"));
        enrichmentService.enriquecer(paciente.getId(), clinica.getId(), true);
    }
}
