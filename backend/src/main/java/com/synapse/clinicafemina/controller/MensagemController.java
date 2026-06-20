package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.dto.EnviarMensagemRequest;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.domain.MidiaMensagem;
import com.synapse.clinicafemina.integration.WhatsappMediaService;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.MensagemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.nio.charset.StandardCharsets;

/**
 * Controller REST para envio de mensagens outbound.
 *
 * Endpoints:
 * <ul>
 *   <li>POST /api/atendimentos/{id}/mensagens — envia mensagem de texto</li>
 *   <li>POST /api/atendimentos/{id}/uploads-midia — upload de arquivo de mídia</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/atendimentos/{atendimentoId}")
@RequiredArgsConstructor
public class MensagemController {

    private final MensagemService mensagemService;
    private final ClinicaConfigService clinicaConfigService;
    private final WhatsappMediaService whatsappMediaService;

    /**
     * Envia uma mensagem outbound para o paciente via WhatsApp.
     */
    @PostMapping("/mensagens")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public MensagemDTO enviar(
            @PathVariable Long atendimentoId,
            @RequestBody @Valid EnviarMensagemRequest req,
            @AuthenticationPrincipal Usuario usuario) {
        Long remetenteId = usuario != null ? usuario.getId() : null;
        Long clinicaId = clinicaConfigService.obterClinicaAtual().getId();
        return mensagemService.enviar(atendimentoId, clinicaId, req, remetenteId);
    }

    /**
     * Recebe um arquivo de mídia (imagem, áudio, documento), faz upload para
     * a Meta Cloud API e envia como mensagem outbound.
     *
     * Suporta tipos: image/jpeg, image/png, audio/ogg, application/pdf.
     * Tamanho máximo: configurado via {@code spring.servlet.multipart.max-file-size}.
     */
    @PostMapping(value = "/uploads-midia", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public MensagemDTO uploadMidia(
            @PathVariable Long atendimentoId,
            @RequestPart("arquivo") MultipartFile arquivo,
            @AuthenticationPrincipal Usuario usuario) {
        Long remetenteId = usuario != null ? usuario.getId() : null;
        Long clinicaId = clinicaConfigService.obterClinicaAtual().getId();
        return mensagemService.enviarMidia(atendimentoId, clinicaId, arquivo, remetenteId);
    }

    @GetMapping("/mensagens/{mensagemId}/midia")
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public ResponseEntity<StreamingResponseBody> baixarMidia(
            @PathVariable Long atendimentoId,
            @PathVariable Long mensagemId
    ) {
        Long clinicaId = clinicaConfigService.obterClinicaAtual().getId();
        MidiaMensagem midia = mensagemService.buscarMidia(atendimentoId, mensagemId, clinicaId);
        if (midia.getWhatsappMediaId() == null || midia.getWhatsappMediaId().isBlank()) {
            throw new IllegalStateException("A mídia não está disponível na Meta");
        }

        String nome = midia.getNomeArquivo() == null ? "arquivo" : midia.getNomeArquivo();
        StreamingResponseBody body = output ->
                whatsappMediaService.copiarPara(midia.getWhatsappMediaId(), output);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(midia.getMimeType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + java.net.URLEncoder.encode(
                                nome, StandardCharsets.UTF_8
                        ).replace("+", "%20")
                )
                .body(body);
    }
}
