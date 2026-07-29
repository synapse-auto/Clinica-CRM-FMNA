package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.paciente.importacao.ImportacaoCsvContatoMapping;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportacaoCsvContatoServiceTest {

    @Mock private PacienteRepository pacienteRepository;
    @Mock private ClinicaRepository clinicaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private WhatsappPhoneIdentityService phoneIdentityService;

    private ImportacaoCsvContatoService service;
    private Clinica clinica;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new ImportacaoCsvContatoService(
                new ImportacaoCsvContatoParser(), pacienteRepository, clinicaRepository, usuarioRepository,
                phoneIdentityService
        );
        clinica = new Clinica();
        clinica.setId(1L);
        usuario = new Recepcionista();
        usuario.setId(9L);
        when(usuarioRepository.findAtivoByIdAndClinicaId(9L, 1L)).thenReturn(Optional.of(usuario));
        when(phoneIdentityService.identify(any())).thenAnswer(invocation -> identity((String) invocation.getArgument(0)));
        lenient().when(pacienteRepository.findByClinicaIdAndTelefoneNormalizadoIn(eq(1L), anyCollection()))
                .thenReturn(List.of());
    }

    @Test
    void should_preview_auto_mapping_and_report_invalid_rows_without_persisting() {
        var preview = service.preview(
                csv("Nome;Celular\nJoão Ávila;5583999999999\n;telefone-inválido\n"),
                null,
                clinica,
                9L
        );

        assertEquals("Nome", preview.suggestedMapping().nameColumn());
        assertEquals("Celular", preview.suggestedMapping().phoneColumn());
        assertEquals(2, preview.totalRows());
        assertEquals(1, preview.validation().valid());
        assertEquals(1, preview.validation().invalid());
        verify(pacienteRepository, never()).saveAll(any());
    }

    @Test
    void should_create_only_new_contacts_with_import_origin_in_authenticated_clinic() {
        var preview = service.preview(csv("nome;telefone\nJoão Ávila;5583999999999\n"), null, clinica, 9L);
        when(clinicaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(clinica));
        when(pacienteRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.importar(
                csv("nome;telefone\nJoão Ávila;5583999999999\n"),
                preview.fileHash(),
                new ImportacaoCsvContatoMapping("nome", "telefone"),
                clinica,
                9L
        );

        ArgumentCaptor<List<Paciente>> captor = ArgumentCaptor.forClass(List.class);
        verify(pacienteRepository).saveAll(captor.capture());
        Paciente created = captor.getValue().getFirst();
        assertEquals(1, result.created());
        assertEquals("IMPORTACAO", created.getOrigem());
        assertEquals("João Ávila", created.getNome());
        assertEquals("JOÃO ÁVILA", created.getNomeBusca());
        assertEquals(1L, created.getClinica().getId());
        assertEquals(usuario, created.getCriadoPor());
        verify(pacienteRepository, never()).findByClinicaIdAndTelefoneNormalizadoIn(eq(2L), anyCollection());
    }

    @Test
    void should_skip_existing_safe_alias_and_duplicate_inside_file_without_updating_existing_contact() {
        Paciente existing = new Paciente();
        existing.setTelefoneNormalizado("558391114004");
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizadoIn(eq(1L), anyCollection()))
                .thenReturn(List.of(existing));
        var file = csv("nome;telefone\nMaria;5583991114004\nOutra Maria;558391114004\n");
        var preview = service.preview(file, null, clinica, 9L);
        when(clinicaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(clinica));

        var result = service.importar(
                file, preview.fileHash(), new ImportacaoCsvContatoMapping("nome", "telefone"), clinica, 9L
        );

        assertEquals(0, result.created());
        assertEquals(1, result.skippedExisting());
        assertEquals(1, result.skippedDuplicateInFile());
        verify(pacienteRepository, never()).saveAll(any());
    }

    @Test
    void should_reject_changed_file_invalid_mapping_and_inactive_user() {
        var preview = service.preview(csv("nome;telefone\nAna;5583999999999\n"), null, clinica, 9L);
        assertThrows(BadRequestException.class, () -> service.importar(
                csv("nome;telefone\nBia;5583999999999\n"), preview.fileHash(),
                new ImportacaoCsvContatoMapping("nome", "telefone"), clinica, 9L
        ));
        assertThrows(BadRequestException.class, () -> service.preview(
                csv("nome;telefone\nAna;5583999999999\n"),
                new ImportacaoCsvContatoMapping("nome", "nome"), clinica, 9L
        ));
        when(usuarioRepository.findAtivoByIdAndClinicaId(9L, 1L)).thenReturn(Optional.empty());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.preview(
                csv("nome;telefone\nAna;5583999999999\n"), null, clinica, 9L
        ));
    }

    private WhatsappPhoneIdentityService.PhoneIdentity identity(String rawPhone) {
        String normalized = com.synapse.clinicafemina.integration.whatsapp.WhatsappPhoneNormalizer.normalize(rawPhone);
        return new WhatsappPhoneIdentityService.PhoneIdentity(
                normalized,
                com.synapse.clinicafemina.integration.whatsapp.WhatsappPhoneNormalizer.safeAliases(normalized)
        );
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "contatos.csv", "text/csv", content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
