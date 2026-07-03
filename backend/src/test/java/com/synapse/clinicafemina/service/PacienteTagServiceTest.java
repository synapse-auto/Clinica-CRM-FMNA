package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.PacienteTag;
import com.synapse.clinicafemina.domain.PacienteTagId;
import com.synapse.clinicafemina.domain.Tag;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.PacienteTagRepository;
import com.synapse.clinicafemina.repository.TagRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacienteTagServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private PacienteTagRepository pacienteTagRepository;

    private PacienteTagService service;
    private Clinica clinica;
    private Paciente paciente;
    private Tag tag;

    @BeforeEach
    void setUp() {
        service = new PacienteTagService(pacienteRepository, tagRepository, pacienteTagRepository);
        clinica = new Clinica();
        clinica.setId(7L);

        paciente = new Paciente();
        paciente.setId(3L);
        paciente.setClinica(clinica);

        tag = new Tag();
        tag.setId(11L);
        tag.setClinica(clinica);
        tag.setNome("Prioridade");
        tag.setCor("#ef4444");
        tag.setAtivo(true);
        tag.setCriadoEm(OffsetDateTime.now());
        tag.setAtualizadoEm(OffsetDateTime.now());
    }

    @Test
    void should_add_active_tag_to_patient_in_current_clinic() {
        when(pacienteRepository.findByIdAndClinicaId(3L, 7L)).thenReturn(Optional.of(paciente));
        when(tagRepository.findByIdAndClinicaIdAndDeletadoEmIsNull(11L, 7L)).thenReturn(Optional.of(tag));
        when(pacienteTagRepository.existsById(new PacienteTagId(3L, 11L))).thenReturn(false);
        when(pacienteTagRepository.findTagsByPacienteIdAndClinicaId(3L, 7L)).thenReturn(List.of(tag));

        var response = service.adicionar(3L, 11L, 7L);

        ArgumentCaptor<PacienteTag> captor = ArgumentCaptor.forClass(PacienteTag.class);
        verify(pacienteTagRepository).save(captor.capture());
        assertEquals(3L, captor.getValue().getPaciente().getId());
        assertEquals(11L, captor.getValue().getTag().getId());
        assertEquals("Prioridade", response.get(0).nome());
    }

    @Test
    void should_reject_inactive_tag_when_linking_to_patient() {
        tag.setAtivo(false);
        when(pacienteRepository.findByIdAndClinicaId(3L, 7L)).thenReturn(Optional.of(paciente));
        when(tagRepository.findByIdAndClinicaIdAndDeletadoEmIsNull(11L, 7L)).thenReturn(Optional.of(tag));

        assertThrows(BadRequestException.class, () -> service.adicionar(3L, 11L, 7L));
        verify(pacienteTagRepository, never()).save(any());
    }

    @Test
    void should_remove_tag_scoped_by_current_clinic() {
        when(pacienteRepository.findByIdAndClinicaId(3L, 7L)).thenReturn(Optional.of(paciente));

        service.remover(3L, 11L, 7L);

        verify(pacienteTagRepository).deleteByPacienteIdAndTagIdAndClinicaId(3L, 11L, 7L);
    }
}
