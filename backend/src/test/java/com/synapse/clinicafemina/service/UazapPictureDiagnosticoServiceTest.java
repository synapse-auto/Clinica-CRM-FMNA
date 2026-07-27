package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.uazap.UazapPictureReprocessarPendentesResponse;
import com.synapse.clinicafemina.integration.whatsapp.uazap.UazapPictureEnrichmentOutcome;
import com.synapse.clinicafemina.integration.whatsapp.uazap.UazapProfilePhotoEnrichmentService;
import com.synapse.clinicafemina.repository.PacienteFotoPerfilRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UazapPictureDiagnosticoServiceTest {

    @Test
    void reprocessesOnlyCandidatesFromTheAuthenticatedClinicAndReturnsSanitizedCounters() {
        PacienteRepository pacienteRepository = mock(PacienteRepository.class);
        PacienteFotoPerfilRepository fotoRepository = mock(PacienteFotoPerfilRepository.class);
        UazapProfilePhotoEnrichmentService enrichmentService = mock(UazapProfilePhotoEnrichmentService.class);
        UazapPictureDiagnosticoService service = new UazapPictureDiagnosticoService(
                pacienteRepository, fotoRepository, enrichmentService
        );
        Clinica clinica = new Clinica();
        clinica.setId(9L);
        when(fotoRepository.findPacientesElegiveisParaReprocessamento(eq(9L), any(), eq(50)))
                .thenReturn(List.of(11L, 12L, 13L));
        when(enrichmentService.enriquecer(11L, 9L)).thenReturn(outcome(true, null));
        when(enrichmentService.enriquecer(12L, 9L)).thenReturn(outcome(false, "FOTO_NAO_ENCONTRADA"));
        when(enrichmentService.enriquecer(13L, 9L)).thenReturn(outcome(false, "FALHA_DE_COMUNICACAO_COM_UAZAP"));

        UazapPictureReprocessarPendentesResponse result = service.reprocessarPendentes(clinica, 50);

        assertThat(result).isEqualTo(new UazapPictureReprocessarPendentesResponse(3, 3, 1, 1, 1, 0));
        verify(enrichmentService).enriquecer(11L, 9L);
        verify(enrichmentService).enriquecer(12L, 9L);
        verify(enrichmentService).enriquecer(13L, 9L);
    }

    private UazapPictureEnrichmentOutcome outcome(boolean persisted, String reason) {
        return new UazapPictureEnrichmentOutcome(
                200, "application/json", 20, "JSON", List.of(), false, false, false,
                null, null, persisted, reason, List.of()
        );
    }
}
