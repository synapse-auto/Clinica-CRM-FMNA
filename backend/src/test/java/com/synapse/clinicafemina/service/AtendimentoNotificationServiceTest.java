package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.repository.NotificacaoAtendimentoRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtendimentoNotificationServiceTest {

    @Mock
    private NotificacaoAtendimentoRepository repository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Test
    void should_notify_only_active_receptionists_returned_for_the_same_clinic() {
        Clinica clinica = new Clinica();
        clinica.setId(1L);
        Atendimento atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);

        Recepcionista recepcionista = new Recepcionista();
        recepcionista.setId(10L);
        recepcionista.setClinica(clinica);
        Mensagem resumo = new Mensagem();
        resumo.setId(88L);
        resumo.setAtendimento(atendimento);

        when(usuarioRepository.findRecepcionistasAtivosByClinicaId(1L))
                .thenReturn(List.of(recepcionista));
        when(repository.existsByUsuarioIdAndAtendimentoIdAndTipoDesde(
                eq(10L), eq(30L), eq("TRANSFERENCIA_IA"), any()))
                .thenReturn(false);

        AtendimentoNotificationService service = new AtendimentoNotificationService(repository, usuarioRepository);

        int created = service.notificarTransferenciaIa(atendimento, resumo);

        assertEquals(1, created);
        verify(usuarioRepository).findRecepcionistasAtivosByClinicaId(1L);
        verify(repository).save(any());
    }

    @Test
    void should_notify_gestor_selected_as_transfer_destination() {
        Clinica clinica = new Clinica();
        clinica.setId(1L);
        Atendimento atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        Gestor gestor = new Gestor();
        gestor.setId(1L);
        gestor.setClinica(clinica);
        gestor.setPerfil("GESTOR");

        when(usuarioRepository.findRecepcionistasAtivosByClinicaId(1L)).thenReturn(List.of());
        when(repository.existsByUsuarioIdAndAtendimentoIdAndTipoDesde(
                eq(1L), eq(30L), eq("TRANSFERENCIA_IA"), any()))
                .thenReturn(false);

        AtendimentoNotificationService service = new AtendimentoNotificationService(repository, usuarioRepository);
        var result = service.notificarTransferenciaIa(
                atendimento, null, "N8N", gestor, java.time.OffsetDateTime.now()
        );

        assertEquals(1, result.criadas());
        assertEquals(List.of(1L), result.destinatariosCriados());
        verify(repository).save(any());
    }
}
