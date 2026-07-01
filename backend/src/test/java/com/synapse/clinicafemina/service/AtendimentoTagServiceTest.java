package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.AtendimentoTag;
import com.synapse.clinicafemina.domain.AtendimentoTagId;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Tag;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.AtendimentoTagRepository;
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
class AtendimentoTagServiceTest {

    @Mock
    private AtendimentoRepository atendimentoRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private AtendimentoTagRepository atendimentoTagRepository;

    private AtendimentoTagService service;
    private Clinica clinica;
    private Atendimento atendimento;
    private Tag tag;

    @BeforeEach
    void setUp() {
        service = new AtendimentoTagService(atendimentoRepository, tagRepository, atendimentoTagRepository);
        clinica = new Clinica();
        clinica.setId(7L);

        Paciente paciente = new Paciente();
        paciente.setId(2L);
        paciente.setClinica(clinica);

        atendimento = new Atendimento();
        atendimento.setId(3L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);

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
    void should_add_active_tag_to_atendimento_in_current_clinic() {
        when(atendimentoRepository.findByIdAndClinicaId(3L, 7L)).thenReturn(Optional.of(atendimento));
        when(tagRepository.findByIdAndClinicaIdAndDeletadoEmIsNull(11L, 7L)).thenReturn(Optional.of(tag));
        when(atendimentoTagRepository.existsById(new AtendimentoTagId(3L, 11L))).thenReturn(false);
        when(atendimentoTagRepository.findTagsByAtendimentoIdAndClinicaId(3L, 7L)).thenReturn(List.of(tag));

        var response = service.adicionar(3L, 11L, 7L);

        ArgumentCaptor<AtendimentoTag> captor = ArgumentCaptor.forClass(AtendimentoTag.class);
        verify(atendimentoTagRepository).save(captor.capture());
        assertEquals(3L, captor.getValue().getAtendimento().getId());
        assertEquals(11L, captor.getValue().getTag().getId());
        assertEquals("Prioridade", response.get(0).nome());
    }

    @Test
    void should_reject_inactive_tag_when_linking_to_atendimento() {
        tag.setAtivo(false);
        when(atendimentoRepository.findByIdAndClinicaId(3L, 7L)).thenReturn(Optional.of(atendimento));
        when(tagRepository.findByIdAndClinicaIdAndDeletadoEmIsNull(11L, 7L)).thenReturn(Optional.of(tag));

        assertThrows(BadRequestException.class, () -> service.adicionar(3L, 11L, 7L));
        verify(atendimentoTagRepository, never()).save(any());
    }

    @Test
    void should_remove_tag_scoped_by_current_clinic() {
        when(atendimentoRepository.findByIdAndClinicaId(3L, 7L)).thenReturn(Optional.of(atendimento));

        service.remover(3L, 11L, 7L);

        verify(atendimentoTagRepository).deleteByAtendimentoIdAndTagIdAndClinicaId(3L, 11L, 7L);
    }
}
