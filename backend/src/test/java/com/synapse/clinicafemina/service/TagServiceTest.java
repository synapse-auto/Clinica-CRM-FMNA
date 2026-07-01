package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Tag;
import com.synapse.clinicafemina.dto.operacional.StatusRequest;
import com.synapse.clinicafemina.dto.operacional.TagRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository repository;

    private TagService service;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        service = new TagService(repository);
        clinica = new Clinica();
        clinica.setId(7L);
    }

    @Test
    void should_create_tag_for_current_clinic() {
        TagRequest request = new TagRequest(" Prioridade ", "#0d9488", "Pacientes prioritarias", true);
        when(repository.existsByClinicaIdAndNomeIgnoreCaseAndDeletadoEmIsNull(7L, "Prioridade"))
                .thenReturn(false);
        when(repository.save(any(Tag.class))).thenAnswer(invocation -> {
            Tag saved = invocation.getArgument(0);
            saved.setId(15L);
            return saved;
        });

        var response = service.criar(clinica, request);

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(repository).save(captor.capture());
        Tag saved = captor.getValue();
        assertEquals(clinica, saved.getClinica());
        assertEquals("Prioridade", saved.getNome());
        assertEquals("#0d9488", saved.getCor());
        assertEquals("Pacientes prioritarias", saved.getDescricao());
        assertTrue(saved.getAtivo());
        assertEquals(15L, response.id());
    }

    @Test
    void should_reject_duplicate_active_tag_name_in_clinic() {
        TagRequest request = new TagRequest("Prioridade", "#0d9488", null, true);
        when(repository.existsByClinicaIdAndNomeIgnoreCaseAndDeletadoEmIsNull(7L, "Prioridade"))
                .thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.criar(clinica, request));
        verify(repository, never()).save(any());
    }

    @Test
    void should_soft_delete_tag_by_clinic() {
        Tag tag = tag(9L, "Retorno");
        when(repository.findByIdAndClinicaIdAndDeletadoEmIsNull(9L, 7L)).thenReturn(Optional.of(tag));

        service.excluir(clinica, 9L);

        assertNotNull(tag.getDeletadoEm());
    }

    @Test
    void should_update_tag_status_by_clinic() {
        Tag tag = tag(11L, "VIP");
        tag.setAtivo(true);
        when(repository.findByIdAndClinicaIdAndDeletadoEmIsNull(11L, 7L)).thenReturn(Optional.of(tag));
        when(repository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.alterarStatus(clinica, 11L, new StatusRequest(false));

        assertEquals(false, tag.getAtivo());
        assertEquals(false, response.ativo());
    }

    private Tag tag(Long id, String nome) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setClinica(clinica);
        tag.setNome(nome);
        tag.setCor("#0d9488");
        tag.setAtivo(true);
        tag.setCriadoEm(OffsetDateTime.now());
        tag.setAtualizadoEm(OffsetDateTime.now());
        return tag;
    }
}
