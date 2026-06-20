package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.AtendenteOptionDTO;
import com.synapse.clinicafemina.dto.AtendimentoDetalheDTO;
import com.synapse.clinicafemina.dto.AtendimentoResumoDTO;
import com.synapse.clinicafemina.dto.ConvenioReviewRequest;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.service.AtendimentoService;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.ConvenioReviewService;
import com.synapse.clinicafemina.service.MensagemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/atendimentos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
public class AtendimentoController {

    private final AtendimentoService atendimentoService;
    private final MensagemService mensagemService;
    private final ConvenioReviewService convenioReviewService;
    private final ClinicaConfigService clinicaConfigService;

    @GetMapping
    public Page<AtendimentoResumoDTO> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "TODOS") String tipo,
            @RequestParam(required = false, defaultValue = "TODOS") String filtro,
            @RequestParam(required = false) String busca,
            @AuthenticationPrincipal Usuario usuario,
            @PageableDefault(size = 50, sort = "ultimaMensagemEm") Pageable pageable
    ) {
        return atendimentoService.listar(
                clinicaId(), status, tipo, filtro, busca, usuario.getId(), pageable
        );
    }

    @GetMapping("/atendentes")
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public List<AtendenteOptionDTO> listarAtendentes() {
        return atendimentoService.listarAtendentes(clinicaId());
    }

    @GetMapping("/{id}")
    public AtendimentoDetalheDTO buscar(@PathVariable Long id) {
        return atendimentoService.buscarPorId(id, clinicaId());
    }

    @GetMapping("/{id}/mensagens")
    public Page<MensagemDTO> mensagens(
            @PathVariable Long id,
            @PageableDefault(size = 100, sort = "dataHora") Pageable pageable
    ) {
        return mensagemService.listarHistorico(id, clinicaId(), pageable);
    }

    @PatchMapping("/{id}/leitura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarComoLido(@PathVariable Long id) {
        atendimentoService.marcarComoLido(id, clinicaId());
    }

    @PostMapping("/{id}/transferir")
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public AtendimentoDetalheDTO transferir(
            @PathVariable Long id,
            @RequestBody @Valid TransferirAtendimentoRequest request,
            @AuthenticationPrincipal Usuario usuario
    ) {
        return atendimentoService.transferir(id, request, clinicaId(), usuario.getId());
    }

    @PostMapping("/{id}/assumir")
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public AtendimentoDetalheDTO assumir(
            @PathVariable Long id,
            @AuthenticationPrincipal Usuario usuario
    ) {
        return atendimentoService.assumir(id, clinicaId(), usuario.getId());
    }

    @PatchMapping("/{id}/convenio")
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public AtendimentoDetalheDTO revisarConvenio(
            @PathVariable Long id,
            @RequestBody @Valid ConvenioReviewRequest request,
            @AuthenticationPrincipal Usuario usuario
    ) {
        Long clinicaId = clinicaId();
        convenioReviewService.revisar(id, clinicaId, usuario.getId(), request);
        return atendimentoService.buscarPorId(id, clinicaId);
    }

    @PostMapping("/{id}/encerrar")
    public AtendimentoDetalheDTO encerrar(
            @PathVariable Long id,
            @RequestParam(required = false) String motivo
    ) {
        return atendimentoService.encerrar(id, clinicaId(), motivo);
    }

    private Long clinicaId() {
        return clinicaConfigService.obterClinicaAtual().getId();
    }
}
