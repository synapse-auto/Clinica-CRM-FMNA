package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.service.PacienteFotoPerfilService;
import com.synapse.clinicafemina.service.PacienteFotoPerfilService.TentativaFoto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UazapProfilePhotoEnrichmentServiceTest {

    @Mock private UazapProfilePhotoClient photoClient;
    @Mock private UazapPicturePayloadParser payloadParser;
    @Mock private UazapProfilePhotoDownloader photoDownloader;
    @Mock private PacienteFotoPerfilService fotoPerfilService;

    private WhatsappProperties properties;
    private UazapProfilePhotoEnrichmentService service;
    private TentativaFoto tentativa;

    @BeforeEach
    void setUp() {
        properties = new WhatsappProperties();
        service = new UazapProfilePhotoEnrichmentService(
                properties, photoClient, payloadParser, photoDownloader, fotoPerfilService
        );
        tentativa = new TentativaFoto(1L, 2L, "5511999990000", 1);
    }

    @Test
    void metaProviderNeverStartsEnrichment() {
        properties.setProvider("META");
        UazapPictureEnrichmentOutcome result = service.enriquecer(1L, 2L);
        assertThat(result.motivoNaoPersistida()).isEqualTo("PROVIDER_ATIVO_NAO_E_UAZAP");
        verify(fotoPerfilService, never()).iniciar(any(), any(), any(Boolean.class));
        verify(photoClient, never()).buscarFotoPerfil(anyString());
    }

    @Test
    void activeCooldownDoesNotCallUazap() {
        properties.setProvider("UAZAP");
        when(fotoPerfilService.iniciar(1L, 2L, false)).thenReturn(Optional.empty());
        UazapPictureEnrichmentOutcome result = service.enriquecer(1L, 2L);
        assertThat(result.motivoNaoPersistida())
                .isEqualTo("COOLDOWN_EM_EXECUCAO_OU_PACIENTE_INVALIDO");
        verify(photoClient, never()).buscarFotoPerfil(anyString());
    }

    @Test
    void binaryImageIsPersistedWithoutExternalDownload() {
        properties.setProvider("UAZAP");
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
        UazapPictureRawResponse raw = new UazapPictureRawResponse(200, "image/jpeg", jpeg);
        UazapPictureEnrichmentOutcome parsed = outcome("IMAGEM", null, null);
        when(fotoPerfilService.iniciar(1L, 2L, false)).thenReturn(Optional.of(tentativa));
        when(photoClient.buscarFotoPerfil(tentativa.telefoneNormalizado())).thenReturn(raw);
        when(payloadParser.extract(raw)).thenReturn(new UazapPictureExtraction(
                parsed, UazapPictureSource.bytes(jpeg, "image/jpeg")
        ));

        UazapPictureEnrichmentOutcome result = service.enriquecer(1L, 2L);

        assertThat(result.fotoPersistida()).isTrue();
        verify(fotoPerfilService).salvarSucesso(
                eq(tentativa),
                argThat(image -> image.contentType().equals("image/jpeg")
                        && java.util.Arrays.equals(image.bytes(), jpeg))
        );
        verify(photoDownloader, never()).baixar(any());
    }

    @Test
    void signedUrlIsDownloadedThenPersisted() {
        properties.setProvider("UAZAP");
        URI uri = URI.create("https://cdn.example/foto.jpg?signature=ficticia");
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        UazapPictureRawResponse raw =
                new UazapPictureRawResponse(200, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
        UazapProfilePhotoImageValidator.ValidatedImage image =
                new UazapProfilePhotoImageValidator.ValidatedImage(png, "image/png");
        when(fotoPerfilService.iniciar(1L, 2L, false)).thenReturn(Optional.of(tentativa));
        when(photoClient.buscarFotoPerfil(tentativa.telefoneNormalizado())).thenReturn(raw);
        when(payloadParser.extract(raw)).thenReturn(new UazapPictureExtraction(
                outcome("JSON", uri.toString(), null), UazapPictureSource.url(uri)
        ));
        when(photoDownloader.baixar(uri)).thenReturn(image);

        assertThat(service.enriquecer(1L, 2L).fotoPersistida()).isTrue();
        verify(fotoPerfilService).salvarSucesso(tentativa, image);
    }

    @Test
    void missingPhotoRegistersCooldown() {
        properties.setProvider("UAZAP");
        UazapPictureRawResponse raw =
                new UazapPictureRawResponse(200, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
        UazapPictureEnrichmentOutcome parsed =
                UazapPictureEnrichmentOutcome.semTentativa("NENHUM_CAMPO_DE_FOTO_RECONHECIDO");
        when(fotoPerfilService.iniciar(1L, 2L, false)).thenReturn(Optional.of(tentativa));
        when(photoClient.buscarFotoPerfil(anyString())).thenReturn(raw);
        when(payloadParser.extract(raw)).thenReturn(UazapPictureExtraction.semFonte(parsed));

        assertThat(service.enriquecer(1L, 2L).fotoPersistida()).isFalse();
        verify(fotoPerfilService)
                .registrarSemFoto(tentativa, "NENHUM_CAMPO_DE_FOTO_RECONHECIDO");
    }

    @Test
    void clientFailureIsRecordedAndNeverPropagates() {
        properties.setProvider("UAZAP");
        when(fotoPerfilService.iniciar(1L, 2L, false)).thenReturn(Optional.of(tentativa));
        when(photoClient.buscarFotoPerfil(anyString())).thenThrow(new RuntimeException("timeout ficticio"));

        assertThatCode(() -> service.enriquecer(1L, 2L)).doesNotThrowAnyException();
        verify(fotoPerfilService)
                .registrarFalha(tentativa, "FALHA_DE_COMUNICACAO_COM_UAZAP", true);
    }

    @Test
    void permanentDownloadFailureDoesNotPersist() {
        properties.setProvider("UAZAP");
        URI uri = URI.create("https://cdn.example/foto.jpg");
        UazapPictureRawResponse raw = new UazapPictureRawResponse(200, "application/json", new byte[] {1});
        when(fotoPerfilService.iniciar(1L, 2L, false)).thenReturn(Optional.of(tentativa));
        when(photoClient.buscarFotoPerfil(anyString())).thenReturn(raw);
        when(payloadParser.extract(raw)).thenReturn(new UazapPictureExtraction(
                outcome("JSON", uri.toString(), null), UazapPictureSource.url(uri)
        ));
        when(photoDownloader.baixar(uri))
                .thenThrow(UazapProfilePhotoDownloadException.permanente("MAGIC_BYTES_INVALIDOS"));

        UazapPictureEnrichmentOutcome result = service.enriquecer(1L, 2L);

        assertThat(result.motivoNaoPersistida()).isEqualTo("MAGIC_BYTES_INVALIDOS");
        verify(fotoPerfilService).registrarFalha(tentativa, "MAGIC_BYTES_INVALIDOS", false);
        verify(fotoPerfilService, never()).salvarSucesso(any(), any());
    }

    @Test
    void unauthorizedHostUsesRecoverableFailureInsteadOfThirtyDayPermanentCooldown() {
        properties.setProvider("UAZAP");
        URI uri = URI.create("https://pps.whatsapp.net/foto.jpg");
        UazapPictureRawResponse raw = new UazapPictureRawResponse(200, "application/json", new byte[] {1});
        when(fotoPerfilService.iniciar(1L, 2L, false)).thenReturn(Optional.of(tentativa));
        when(photoClient.buscarFotoPerfil(anyString())).thenReturn(raw);
        when(payloadParser.extract(raw)).thenReturn(new UazapPictureExtraction(
                outcome("JSON", uri.toString(), null), UazapPictureSource.url(uri)
        ));
        when(photoDownloader.baixar(uri))
                .thenThrow(UazapProfilePhotoDownloadException.permanente("HOST_DE_FOTO_NAO_AUTORIZADO"));

        service.enriquecer(1L, 2L);

        verify(fotoPerfilService).registrarFalha(tentativa, "HOST_DE_FOTO_NAO_AUTORIZADO", true);
    }

    private UazapPictureEnrichmentOutcome outcome(String formato, String fotoUrl, String motivo) {
        return new UazapPictureEnrichmentOutcome(
                200, "application/json", 10, formato, List.of("data"),
                fotoUrl != null, fotoUrl != null && fotoUrl.contains("?"), false,
                fotoUrl == null ? null : "cdn.example", fotoUrl, false, motivo,
                List.of("data:object")
        );
    }
}
