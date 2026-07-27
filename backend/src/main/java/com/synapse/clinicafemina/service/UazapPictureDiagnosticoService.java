package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.uazap.UazapPictureDiagnosticoResponse;
import com.synapse.clinicafemina.dto.uazap.UazapPictureReprocessarPendentesResponse;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.integration.whatsapp.uazap.UazapPictureEnrichmentOutcome;
import com.synapse.clinicafemina.integration.whatsapp.uazap.UazapProfilePhotoEnrichmentService;
import com.synapse.clinicafemina.repository.PacienteFotoPerfilRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Orquestra o diagnóstico administrativo: garante que o paciente pertence à clínica do admin
 * autenticado, então delega a mesma lógica de produção ({@link UazapProfilePhotoEnrichmentService})
 * usada pelo enriquecimento automático do webhook.
 */
@Service
@RequiredArgsConstructor
public class UazapPictureDiagnosticoService {

    private final PacienteRepository pacienteRepository;
    private final PacienteFotoPerfilRepository fotoPerfilRepository;
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

    public UazapPictureReprocessarPendentesResponse reprocessarPendentes(Clinica clinica, int limite) {
        var candidatos = fotoPerfilRepository.findPacientesElegiveisParaReprocessamento(
                clinica.getId(), OffsetDateTime.now(), limite
        );
        int processados = 0;
        int fotosPersistidas = 0;
        int semFoto = 0;
        int falhasTemporarias = 0;
        int falhasPermanentes = 0;

        for (Long pacienteId : candidatos) {
            UazapPictureEnrichmentOutcome outcome = enrichmentService.enriquecer(pacienteId, clinica.getId());
            processados++;
            if (outcome.fotoPersistida()) {
                fotosPersistidas++;
            } else if (isTemporary(outcome.motivoNaoPersistida())) {
                falhasTemporarias++;
            } else if (isNoPhoto(outcome.motivoNaoPersistida())) {
                semFoto++;
            } else {
                falhasPermanentes++;
            }
        }
        return new UazapPictureReprocessarPendentesResponse(
                candidatos.size(), processados, fotosPersistidas, semFoto, falhasTemporarias, falhasPermanentes
        );
    }

    private boolean isTemporary(String motivo) {
        return motivo != null && (motivo.startsWith("FALHA_DE_COMUNICACAO")
                || motivo.startsWith("FALHA_HTTP_FOTO_5")
                || motivo.equals("HOST_DE_FOTO_INDISPONIVEL")
                || motivo.equals("DOWNLOAD_INTERROMPIDO"));
    }

    private boolean isNoPhoto(String motivo) {
        return motivo != null && (motivo.equals("FOTO_NAO_ENCONTRADA")
                || motivo.equals("NENHUM_CAMPO_DE_FOTO_RECONHECIDO"));
    }
}
