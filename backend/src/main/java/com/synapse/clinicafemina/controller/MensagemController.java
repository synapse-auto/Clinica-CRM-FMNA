package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.dto.EnviarMensagemRequest;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.service.MensagemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para envio de mensagens outbound.
 *
 * Endpoints:
 * <ul>
 *   <li>POST /api/atendimentos/{id}/mensagens — envia mensagem de texto</li>
 *   <li>POST /api/atendimentos/{id}/uploads-midia — placeholder para upload de mídia</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/atendimentos/{atendimentoId}")
@RequiredArgsConstructor
public class MensagemController {

    private final MensagemService mensagemService;

    /**
     * Envia uma mensagem outbound para o paciente via WhatsApp.
     *
     * @param atendimentoId ID do atendimento
     * @param req           Corpo da requisição (tipoMedia + conteudo)
     * @param userDetails   Usuário autenticado (extraído do JWT)
     */
    @PostMapping("/mensagens")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public MensagemDTO enviar(
            @PathVariable Long atendimentoId,
            @RequestBody @Valid EnviarMensagemRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        // Mapeia o email do usuário autenticado para o ID — simplificado;
        // em produção: injetar UsuarioRepository e buscar pelo username.
        Long remetenteId = null; // TODO: resolver via UsuarioRepository
        return mensagemService.enviar(atendimentoId, req, remetenteId);
    }

    /**
     * Placeholder para upload de mídia (imagens, áudio, documentos).
     * Implementação completa na Fase 3 (integração de mídia).
     */
    @PostMapping("/uploads-midia")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public void uploadMidia(@PathVariable Long atendimentoId) {
        throw new UnsupportedOperationException("Upload de mídia será implementado na Fase 3");
    }
}
