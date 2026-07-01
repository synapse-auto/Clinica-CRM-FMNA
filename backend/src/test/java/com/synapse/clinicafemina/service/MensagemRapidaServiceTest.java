package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.CategoriaMensagemRapida;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.MensagemRapida;
import com.synapse.clinicafemina.dto.operacional.MensagemRapidaRequest;
import com.synapse.clinicafemina.dto.operacional.StatusRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.CategoriaMensagemRapidaRepository;
import com.synapse.clinicafemina.repository.MensagemRapidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MensagemRapidaServiceTest {

    @Mock
    private MensagemRapidaRepository repository;

    @Mock
    private CategoriaMensagemRapidaRepository categoriaRepository;

    private MensagemRapidaService service;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        service = new MensagemRapidaService(repository, categoriaRepository);
        clinica = new Clinica();
        clinica.setId(7L);
    }

    @Test
    void should_create_quick_message_for_current_clinic() {
        CategoriaMensagemRapida categoria = categoria((short) 2, "AGENDAMENTO", "Agendamento");
        MensagemRapidaRequest request = new MensagemRapidaRequest(
                (short) 2,
                " Confirmacao ",
                "/confirmar",
                "Sua consulta esta confirmada.",
                true
        );
        when(categoriaRepository.findById((short) 2)).thenReturn(Optional.of(categoria));
        when(repository.existsByClinicaIdAndAtalhoIgnoreCaseAndDeletadoEmIsNull(7L, "/confirmar"))
                .thenReturn(false);
        when(repository.save(any(MensagemRapida.class))).thenAnswer(invocation -> {
            MensagemRapida saved = invocation.getArgument(0);
            saved.setId(21L);
            return saved;
        });

        var response = service.criar(clinica, request);

        ArgumentCaptor<MensagemRapida> captor = ArgumentCaptor.forClass(MensagemRapida.class);
        verify(repository).save(captor.capture());
        MensagemRapida saved = captor.getValue();
        assertEquals(clinica, saved.getClinica());
        assertEquals(categoria, saved.getCategoria());
        assertEquals("Confirmacao", saved.getTitulo());
        assertEquals("/confirmar", saved.getAtalho());
        assertEquals("Sua consulta esta confirmada.", saved.getConteudo());
        assertEquals(21L, response.id());
    }

    @Test
    void should_reject_duplicate_active_shortcut_in_clinic() {
        MensagemRapidaRequest request = new MensagemRapidaRequest(
                null,
                "Confirmacao",
                "/confirmar",
                "Texto",
                true
        );
        when(repository.existsByClinicaIdAndAtalhoIgnoreCaseAndDeletadoEmIsNull(7L, "/confirmar"))
                .thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.criar(clinica, request));
        verify(repository, never()).save(any());
    }

    @Test
    void should_soft_delete_quick_message_by_clinic() {
        MensagemRapida mensagem = mensagem(8L);
        when(repository.findByIdAndClinicaIdAndDeletadoEmIsNull(8L, 7L)).thenReturn(Optional.of(mensagem));

        service.excluir(clinica, 8L);

        assertNotNull(mensagem.getDeletadoEm());
    }

    @Test
    void should_update_quick_message_status_by_clinic() {
        MensagemRapida mensagem = mensagem(8L);
        mensagem.setAtivo(true);
        when(repository.findByIdAndClinicaIdAndDeletadoEmIsNull(8L, 7L)).thenReturn(Optional.of(mensagem));
        when(repository.save(any(MensagemRapida.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.alterarStatus(clinica, 8L, new StatusRequest(false));

        assertEquals(false, mensagem.getAtivo());
        assertEquals(false, response.ativo());
    }

    private MensagemRapida mensagem(Long id) {
        MensagemRapida mensagem = new MensagemRapida();
        mensagem.setId(id);
        mensagem.setClinica(clinica);
        mensagem.setTitulo("Confirmacao");
        mensagem.setAtalho("/confirmar");
        mensagem.setConteudo("Texto");
        mensagem.setAtivo(true);
        return mensagem;
    }

    private CategoriaMensagemRapida categoria(short id, String codigo, String rotulo) {
        CategoriaMensagemRapida categoria = new CategoriaMensagemRapida();
        categoria.setId(id);
        categoria.setCodigo(codigo);
        categoria.setRotulo(rotulo);
        categoria.setCor("#0d9488");
        return categoria;
    }
}
