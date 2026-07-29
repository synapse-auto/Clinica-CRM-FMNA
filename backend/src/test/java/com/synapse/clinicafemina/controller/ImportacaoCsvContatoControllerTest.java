package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.paciente.importacao.ImportacaoCsvContatoMapping;
import com.synapse.clinicafemina.dto.paciente.importacao.ImportacaoCsvContatoPreview;
import com.synapse.clinicafemina.dto.paciente.importacao.ImportacaoCsvContatoResumo;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.ImportacaoCsvContatoService;
import com.synapse.clinicafemina.service.PacienteFotoPerfilService;
import com.synapse.clinicafemina.service.PacienteService;
import com.synapse.clinicafemina.service.PacienteTagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportacaoCsvContatoControllerTest {

    @Mock private ClinicaConfigService clinicaConfigService;
    @Mock private PacienteService pacienteService;
    @Mock private PacienteTagService pacienteTagService;
    @Mock private PacienteFotoPerfilService fotoService;
    @Mock private ImportacaoCsvContatoService importacaoService;

    @Test
    void should_delegate_csv_preview_with_clinic_from_context_and_authenticated_user() {
        Clinica clinica = new Clinica();
        clinica.setId(7L);
        Usuario usuario = new Recepcionista();
        usuario.setId(3L);
        ImportacaoCsvContatoPreview preview = new ImportacaoCsvContatoPreview(
                "a".repeat(64), "contatos.csv", "UTF-8", ";", 1,
                List.of("nome", "telefone"), new ImportacaoCsvContatoMapping("nome", "telefone"), List.of(), List.of(),
                new ImportacaoCsvContatoResumo(1, 1, 0, 0, 0, 1, 0, false, List.of())
        );
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);
        when(importacaoService.preview(any(), any(), eq(clinica), eq(3L))).thenReturn(preview);
        PacienteController controller = new PacienteController(
                clinicaConfigService, pacienteService, pacienteTagService, fotoService, importacaoService
        );
        MockMultipartFile file = new MockMultipartFile("file", "contatos.csv", "text/csv", "nome;telefone\nAna;1".getBytes());

        var response = controller.previewImportacaoCsv(file, null, usuario);

        assertSame(preview, response);
        verify(importacaoService).preview(file, null, clinica, 3L);
    }
}
