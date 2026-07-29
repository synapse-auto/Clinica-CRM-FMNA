package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.PacienteFotoPerfilService;
import com.synapse.clinicafemina.service.PacienteService;
import com.synapse.clinicafemina.service.PacienteTagService;
import com.synapse.clinicafemina.service.ImportacaoCsvContatoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacienteFotoControllerTest {

    @Mock private ClinicaConfigService clinicaConfigService;
    @Mock private PacienteService pacienteService;
    @Mock private PacienteTagService pacienteTagService;
    @Mock private PacienteFotoPerfilService fotoService;
    @Mock private ImportacaoCsvContatoService importacaoCsvContatoService;

    private PacienteController controller;

    @BeforeEach
    void setUp() {
        controller = new PacienteController(
                clinicaConfigService,
                pacienteService,
                pacienteTagService,
                fotoService,
                importacaoCsvContatoService
        );
        Clinica clinica = new Clinica();
        clinica.setId(2L);
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);
    }

    @Test
    void returnsStoredPhotoWithContentTypeEtagAndPrivateCache() {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        when(fotoService.obter(10L, 2L))
                .thenReturn(new PacienteFotoPerfilService.FotoArmazenada(
                        png,
                        "image/png",
                        "a".repeat(64)
                ));

        ResponseEntity<byte[]> response = controller.obterFoto(10L, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(response.getHeaders().getETag()).isEqualTo("\"" + "a".repeat(64) + "\"");
        assertThat(response.getHeaders().getCacheControl()).contains("private");
        assertThat(response.getBody()).containsExactly(png);
    }

    @Test
    void matchingEtagReturnsNotModifiedWithoutBody() {
        String sha = "b".repeat(64);
        when(fotoService.obter(10L, 2L))
                .thenReturn(new PacienteFotoPerfilService.FotoArmazenada(
                        new byte[] {1, 2, 3},
                        "image/jpeg",
                        sha
                ));

        ResponseEntity<byte[]> response = controller.obterFoto(10L, "\"" + sha + "\"");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getBody()).isNull();
    }
}
