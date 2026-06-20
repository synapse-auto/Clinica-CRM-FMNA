package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.NotificacaoAtendimentoDTO;
import com.synapse.clinicafemina.dto.NotificacaoResumoDTO;
import com.synapse.clinicafemina.service.AtendimentoNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/atendimentos/notificacoes")
@PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
@RequiredArgsConstructor
public class AtendimentoNotificationController {

    private final AtendimentoNotificationService service;

    @GetMapping
    public Page<NotificacaoAtendimentoDTO> listar(
            @RequestParam(defaultValue = "true") boolean somenteNaoLidas,
            @AuthenticationPrincipal Usuario usuario,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return service.listar(usuario.getId(), somenteNaoLidas, pageable);
    }

    @GetMapping("/resumo")
    public NotificacaoResumoDTO resumo(@AuthenticationPrincipal Usuario usuario) {
        return service.resumo(usuario.getId());
    }

    @PatchMapping("/{id}/leitura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarComoLida(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        service.marcarComoLida(id, usuario.getId());
    }

    @PatchMapping("/leitura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarTodasComoLidas(@AuthenticationPrincipal Usuario usuario) {
        service.marcarTodasComoLidas(usuario.getId());
    }
}
