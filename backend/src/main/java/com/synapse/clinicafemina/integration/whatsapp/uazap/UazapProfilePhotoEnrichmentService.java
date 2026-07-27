package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.service.PacienteFotoPerfilService;
import com.synapse.clinicafemina.service.PacienteFotoPerfilService.TentativaFoto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UazapProfilePhotoEnrichmentService {

    private final WhatsappProperties whatsappProperties;
    private final UazapProfilePhotoClient photoClient;
    private final UazapPicturePayloadParser payloadParser;
    private final UazapProfilePhotoDownloader photoDownloader;
    private final PacienteFotoPerfilService fotoPerfilService;

    public UazapPictureEnrichmentOutcome enriquecer(Long pacienteId, Long clinicaId) {
        return enriquecer(pacienteId, clinicaId, false);
    }

    public UazapPictureEnrichmentOutcome enriquecer(Long pacienteId, Long clinicaId, boolean forcar) {
        if (whatsappProperties.resolveProvider() != WhatsappProviderType.UAZAP) {
            return UazapPictureEnrichmentOutcome.semTentativa("PROVIDER_ATIVO_NAO_E_UAZAP");
        }

        Optional<TentativaFoto> claim = fotoPerfilService.iniciar(pacienteId, clinicaId, forcar);
        if (claim.isEmpty()) {
            return UazapPictureEnrichmentOutcome.semTentativa("COOLDOWN_EM_EXECUCAO_OU_PACIENTE_INVALIDO");
        }
        TentativaFoto tentativa = claim.get();

        UazapPictureRawResponse raw;
        try {
            raw = photoClient.buscarFotoPerfil(tentativa.telefoneNormalizado());
        } catch (Exception exception) {
            fotoPerfilService.registrarFalha(tentativa, "FALHA_DE_COMUNICACAO_COM_UAZAP", true);
            log.warn("Falha temporaria ao consultar foto UAZAP. tipoErro={}",
                    exception.getClass().getSimpleName());
            return UazapPictureEnrichmentOutcome.semTentativa("FALHA_DE_COMUNICACAO_COM_UAZAP");
        }

        UazapPictureExtraction extraction = payloadParser.extract(raw);
        UazapPictureEnrichmentOutcome outcome = extraction.outcome();
        if (extraction.source() == null) {
            registrarAusenciaOuFalha(tentativa, outcome);
            log.info("Foto UAZAP nao atualizada. statusHttp={}, formato={}, motivo={}",
                    outcome.statusHttp(), outcome.formato(), outcome.motivoNaoPersistida());
            return outcome;
        }

        try {
            UazapProfilePhotoImageValidator.ValidatedImage image =
                    extraction.source().type() == UazapPictureSource.Type.BYTES
                    ? new UazapProfilePhotoImageValidator.ValidatedImage(
                            extraction.source().bytes(),
                            extraction.source().contentType()
                    )
                    : photoDownloader.baixar(extraction.source().url());
            fotoPerfilService.salvarSucesso(tentativa, image);
            log.info("Foto de perfil UAZAP persistida com sucesso. bytes={}, contentType={}",
                    image.bytes().length, image.contentType());
            return outcome.comFotoPersistida();
        } catch (UazapProfilePhotoDownloadException exception) {
            if (exception.semFoto()) {
                fotoPerfilService.registrarSemFoto(tentativa, exception.motivo());
            } else {
                fotoPerfilService.registrarFalha(tentativa, exception.motivo(), exception.temporaria());
            }
            log.warn("Foto UAZAP nao persistida apos download. motivo={}, temporaria={}",
                    exception.motivo(), exception.temporaria());
            return outcome.comMotivo(exception.motivo());
        }
    }

    private void registrarAusenciaOuFalha(
            TentativaFoto tentativa,
            UazapPictureEnrichmentOutcome outcome
    ) {
        Integer status = outcome.statusHttp();
        if (status != null && (status == 429 || status >= 500)) {
            fotoPerfilService.registrarFalha(
                    tentativa,
                    outcome.motivoNaoPersistida(),
                    true
            );
            return;
        }
        if (status != null && status >= 400 && status != 404 && status != 410) {
            fotoPerfilService.registrarFalha(
                    tentativa,
                    outcome.motivoNaoPersistida(),
                    false
            );
            return;
        }
        fotoPerfilService.registrarSemFoto(tentativa, outcome.motivoNaoPersistida());
    }
}
