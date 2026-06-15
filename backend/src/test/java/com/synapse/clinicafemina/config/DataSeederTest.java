package com.synapse.clinicafemina.config;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.TipoClinica;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private ClinicaRepository clinicaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void should_create_dev_clinic_from_environment_when_seed_enabled() {
        DataSeeder seeder = new DataSeeder(clinicaRepository, usuarioRepository, passwordEncoder);
        ReflectionTestUtils.setField(seeder, "seedEnabled", true);
        ReflectionTestUtils.setField(seeder, "seedEmail", "gestor@ultra.local");
        ReflectionTestUtils.setField(seeder, "seedPassword", "senha-local-forte");
        ReflectionTestUtils.setField(seeder, "clinicSlug", "ultramedical");
        ReflectionTestUtils.setField(seeder, "clinicName", "UltraMedical");
        ReflectionTestUtils.setField(seeder, "externalProvider", "MEDWARE");
        ReflectionTestUtils.setField(seeder, "whatsappPhoneNumberId", "phone-ultra");

        when(clinicaRepository.findBySlug("ultramedical")).thenReturn(Optional.empty());
        when(clinicaRepository.save(any(Clinica.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(usuarioRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("senha-local-forte")).thenReturn("hash");

        seeder.run();

        ArgumentCaptor<Clinica> clinicaCaptor = ArgumentCaptor.forClass(Clinica.class);
        ArgumentCaptor<Gestor> gestorCaptor = ArgumentCaptor.forClass(Gestor.class);
        verify(clinicaRepository).save(clinicaCaptor.capture());
        verify(usuarioRepository).save(gestorCaptor.capture());

        Clinica clinica = clinicaCaptor.getValue();
        assertEquals("UltraMedical", clinica.getNome());
        assertEquals("ultramedical", clinica.getSlug());
        assertEquals(TipoClinica.ULTRASSONOGRAFIA, clinica.getTipoClinica());
        assertEquals(ExternalProviderType.MEDWARE, clinica.getExternalProvider());
        assertEquals(false, clinica.getUsaCirurgiasNaAgenda());
        assertEquals("phone-ultra", clinica.getWhatsappPhoneNumberId());
        assertEquals("gestor@ultra.local", gestorCaptor.getValue().getEmail());
        assertNotEquals("senha-local-forte", gestorCaptor.getValue().getSenhaHash());
    }

    @Test
    void should_not_seed_when_seed_disabled() {
        DataSeeder seeder = new DataSeeder(clinicaRepository, usuarioRepository, passwordEncoder);
        ReflectionTestUtils.setField(seeder, "seedEnabled", false);

        seeder.run();

        verify(clinicaRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
    }
}
