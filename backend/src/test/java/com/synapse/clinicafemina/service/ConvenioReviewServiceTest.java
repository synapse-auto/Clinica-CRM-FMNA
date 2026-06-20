package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.*;
import com.synapse.clinicafemina.dto.ConvenioReviewRequest;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConvenioReviewServiceTest {

    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @Test
    void should_persist_reviewer_and_remove_approved_patient_from_review_queue() {
        ConvenioReviewService service = new ConvenioReviewService(
                atendimentoRepository, pacienteRepository, usuarioRepository
        );
        Clinica clinica = new Clinica();
        clinica.setId(1L);
        Paciente paciente = new Paciente();
        paciente.setRequerRevisao(true);
        Atendimento atendimento = new Atendimento();
        atendimento.setPaciente(paciente);
        Gestor gestor = new Gestor();
        gestor.setId(9L);
        gestor.setClinica(clinica);

        when(atendimentoRepository.findByIdAndClinicaId(3L, 1L))
                .thenReturn(Optional.of(atendimento));
        when(usuarioRepository.findAtivoByIdAndClinicaId(9L, 1L))
                .thenReturn(Optional.of(gestor));
        when(pacienteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Paciente result = service.revisar(
                3L, 1L, 9L, new ConvenioReviewRequest("APROVADO")
        );

        assertFalse(result.getRequerRevisao());
        assertEquals("APROVADO", result.getConvenioStatus());
        assertEquals(gestor, result.getConvenioRevisadoPor());
    }
}
