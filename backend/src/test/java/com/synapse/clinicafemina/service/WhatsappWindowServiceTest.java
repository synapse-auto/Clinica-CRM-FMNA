package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.exception.WhatsappWindowClosedException;
import com.synapse.clinicafemina.repository.MensagemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsappWindowServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-16T12:00:00Z");

    @Mock
    private MensagemRepository mensagemRepository;

    private WhatsappWindowService service;

    @BeforeEach
    void setUp() {
        service = new WhatsappWindowService(
                mensagemRepository,
                Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void should_open_window_when_last_inbound_is_less_than_24_hours_old() {
        OffsetDateTime entrada = NOW.minusHours(23);
        when(mensagemRepository.findUltimaMensagemEntradaEm(10L, 1L)).thenReturn(Optional.of(entrada));

        var result = service.avaliar(10L, 1L);

        assertTrue(result.aberta());
        assertEquals(entrada.plusHours(24), result.expiraEm());
    }

    @Test
    void should_keep_window_open_at_exact_24_hour_boundary() {
        when(mensagemRepository.findUltimaMensagemEntradaEm(10L, 1L))
                .thenReturn(Optional.of(NOW.minusHours(24)));

        assertTrue(service.avaliar(10L, 1L).aberta());
    }

    @Test
    void should_close_window_when_last_inbound_is_older_than_24_hours() {
        when(mensagemRepository.findUltimaMensagemEntradaEm(10L, 1L))
                .thenReturn(Optional.of(NOW.minusHours(24).minusNanos(1)));
        when(mensagemRepository.findUltimoTemplateSaidaValidoEm(10L, 1L)).thenReturn(Optional.empty());

        assertFalse(service.avaliar(10L, 1L).aberta());
    }

    @Test
    void should_close_window_when_attendance_has_no_inbound_message() {
        when(mensagemRepository.findUltimaMensagemEntradaEm(10L, 1L)).thenReturn(Optional.empty());
        when(mensagemRepository.findUltimoTemplateSaidaValidoEm(10L, 1L)).thenReturn(Optional.empty());

        assertThrows(WhatsappWindowClosedException.class, () -> service.exigirAberta(10L, 1L));
    }

    @Test
    void should_wait_for_response_when_valid_template_is_after_last_inbound() {
        when(mensagemRepository.findUltimaMensagemEntradaEm(10L, 1L))
                .thenReturn(Optional.of(NOW.minusDays(2)));
        when(mensagemRepository.findUltimoTemplateSaidaValidoEm(10L, 1L))
                .thenReturn(Optional.of(NOW.minusHours(1)));

        var result = service.avaliar(10L, 1L);

        assertFalse(result.aberta());
        assertTrue(result.aguardandoRespostaTemplate());
    }

    @Test
    void should_not_wait_for_response_when_inbound_is_after_template() {
        when(mensagemRepository.findUltimaMensagemEntradaEm(10L, 1L))
                .thenReturn(Optional.of(NOW.minusDays(2)));
        when(mensagemRepository.findUltimoTemplateSaidaValidoEm(10L, 1L))
                .thenReturn(Optional.of(NOW.minusDays(3)));

        assertFalse(service.avaliar(10L, 1L).aguardandoRespostaTemplate());
    }
}
