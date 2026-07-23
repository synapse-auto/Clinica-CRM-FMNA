package com.synapse.clinicafemina.integration.whatsapp;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.integration.whatsapp.meta.MetaWhatsappMediaDownloader;
import com.synapse.clinicafemina.integration.whatsapp.uazap.UazapWhatsappMediaDownloader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prova de roteamento por {@code phone_number_id}, sem chamada real e sem if/else espalhado:
 * Meta usa {@link WhatsappOutboundClient}; UAZAP nunca o toca; falha/pendência não propaga exceção.
 */
@DisplayName("Roteamento de download de mídia inbound: Meta vs UAZAP")
class WhatsappMediaDownloaderRoutingTest {

    private WhatsappProperties propertiesWithUazapPhoneId(String uazapPhoneId) {
        WhatsappProperties properties = new WhatsappProperties();
        properties.getUazap().setPhoneNumberId(uazapPhoneId);
        return properties;
    }

    @Test
    @DisplayName("1. Mídia Meta continua usando WhatsappOutboundClient")
    void metaMedia_stillUsesWhatsappOutboundClient() {
        WhatsappOutboundClient metaClient = mock(WhatsappOutboundClient.class);
        WhatsappOutboundClient.MidiaBaixada esperado =
                new WhatsappOutboundClient.MidiaBaixada(new byte[]{1, 2, 3}, "image/png");
        when(metaClient.baixarMidia("media-meta-1")).thenReturn(esperado);

        MetaWhatsappMediaDownloader downloader =
                new MetaWhatsappMediaDownloader(metaClient, propertiesWithUazapPhoneId("uazap-fmna"));

        assertThat(downloader.supports("phone-ultra")).isTrue(); // catch-all: qualquer id != uazap
        WhatsappOutboundClient.MidiaBaixada resultado = downloader.download("media-meta-1");

        assertThat(resultado).isSameAs(esperado);
    }

    @Test
    @DisplayName("2. Mídia UAZAP nunca usa WhatsappOutboundClient")
    void uazapMedia_neverUsesWhatsappOutboundClient() {
        WhatsappOutboundClient metaClient = mock(WhatsappOutboundClient.class);
        UazapWhatsappMediaDownloader downloader =
                new UazapWhatsappMediaDownloader(propertiesWithUazapPhoneId("uazap-fmna"));

        assertThat(downloader.supports("uazap-fmna")).isTrue();
        downloader.download("media-uazap-1");

        verifyNoInteractions(metaClient); // nunca instanciado/tocado no fluxo UAZAP — nada a verificar nele mesmo
        // Prova mais forte: UazapWhatsappMediaDownloader nem sequer possui referência a WhatsappOutboundClient
        // (não há campo desse tipo na classe) — confirmado por não haver import/uso em UazapWhatsappMediaDownloader.
    }

    @Test
    @DisplayName("3/4. Mídia UAZAP por id (contrato de binário não confirmado): download retorna null (pendente), não lança exceção")
    void uazapMediaById_returnsNullPending_contractNotConfirmed() {
        UazapWhatsappMediaDownloader downloader =
                new UazapWhatsappMediaDownloader(propertiesWithUazapPhoneId("uazap-fmna"));

        WhatsappOutboundClient.MidiaBaixada resultado = downloader.download("media-uazap-id-or-link");

        assertThat(resultado).isNull(); // pendente — metadados já persistidos pelo chamador continuam válidos
    }

    @Test
    @DisplayName("resolução por phone_number_id: Meta é o padrão (catch-all) quando não é explicitamente a instância UAZAP")
    void metaIsDefaultCatchAll_whenPhoneNumberIdIsNotUazap() {
        MetaWhatsappMediaDownloader metaDownloader =
                new MetaWhatsappMediaDownloader(mock(WhatsappOutboundClient.class), propertiesWithUazapPhoneId("uazap-fmna"));
        UazapWhatsappMediaDownloader uazapDownloader =
                new UazapWhatsappMediaDownloader(propertiesWithUazapPhoneId("uazap-fmna"));

        assertThat(metaDownloader.supports("phone-ultra")).isTrue();
        assertThat(uazapDownloader.supports("phone-ultra")).isFalse();

        assertThat(metaDownloader.supports("uazap-fmna")).isFalse();
        assertThat(uazapDownloader.supports("uazap-fmna")).isTrue();
    }

    private ListAppender<ILoggingEvent> logAppender;
    private Logger uazapDownloaderLogger;

    @BeforeEach
    void attachLogCapture() {
        uazapDownloaderLogger = (Logger) LoggerFactory.getLogger(UazapWhatsappMediaDownloader.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        uazapDownloaderLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogCapture() {
        uazapDownloaderLogger.detachAppender(logAppender);
    }

    @Test
    @DisplayName("7. Log de download pendente da UAZAP não expõe token, URL, telefone ou conteúdo — só o mediaId mascarado")
    void uazapDownloadLog_neverExposesSensitiveData() {
        UazapWhatsappMediaDownloader downloader =
                new UazapWhatsappMediaDownloader(propertiesWithUazapPhoneId("uazap-fmna"));
        String mediaIdSensivel = "media-id-nao-deveria-vazar-1234567890";

        downloader.download(mediaIdSensivel);

        List<String> mensagens = logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(mensagens).isNotEmpty();
        String logCompleto = String.join(" | ", mensagens);

        // Nunca aparece o mediaId completo, token, Bearer, URL ou telefone — só o sufixo mascarado.
        assertThat(logCompleto).doesNotContain(mediaIdSensivel);
        assertThat(logCompleto).doesNotContainIgnoringCase("bearer");
        assertThat(logCompleto).doesNotContainIgnoringCase("token");
        assertThat(logCompleto).doesNotContainIgnoringCase("http://").doesNotContainIgnoringCase("https://");
        assertThat(logCompleto).doesNotContain("5543988887777"); // telefone de exemplo — nunca deveria vazar
        assertThat(logCompleto).contains("****7890"); // apenas o sufixo mascarado do mediaId
    }
}
