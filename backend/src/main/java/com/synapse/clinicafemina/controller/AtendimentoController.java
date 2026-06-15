package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.dto.AtendimentoDetalheDTO;
import com.synapse.clinicafemina.dto.AtendimentoResumoDTO;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.service.AtendimentoService;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.MensagemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para operações de atendimento.
 *
 * Endpoints implementados (conforme {@code atendimentos.md}):
 * <ul>
 *   <li>GET  /api/atendimentos</li>
 *   <li>GET  /api/atendimentos/{id}</li>
 *   <li>GET  /api/atendimentos/{id}/mensagens</li>
 *   <li>POST /api/atendimentos/{id}/transferir</li>
 *   <li>POST /api/atendimentos/{id}/encerrar</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/atendimentos")
@RequiredArgsConstructor
public class AtendimentoController {

    private final AtendimentoService atendimentoService;
    private final MensagemService mensagemService;
    private final ClinicaConfigService clinicaConfigService;

    /**
     * Lista atendimentos da clínica com filtros.
     *
     * @param clinicaId  ID da clínica (obrigatório)
     * @param status     Filtro de status: ATIVO, ENCERRADO (opcional)
     * @param tipo       Filtro de tipo: IA, HUMANO, TODOS (opcional)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public Page<AtendimentoResumoDTO> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "TODOS") String tipo,
            @PageableDefault(size = 20, sort = "ultimaMensagemEm") Pageable pageable) {
        Long clinicaId = clinicaConfigService.obterClinicaAtual().getId();
        return atendimentoService.listar(clinicaId, status, tipo, pageable);
    }

    /**
     * Retorna detalhes completos de um atendimento (dados PII descriptografados).
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public AtendimentoDetalheDTO buscar(@PathVariable Long id) {
        Long clinicaId = clinicaConfigService.obterClinicaAtual().getId();
        return atendimentoService.buscarPorId(id, clinicaId);
    }

    /**
     * Histórico de mensagens paginado de um atendimento.
     */
    @GetMapping("/{id}/mensagens")
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public Page<MensagemDTO> mensagens(
            @PathVariable Long id,
            @PageableDefault(size = 30, sort = "dataHora") Pageable pageable) {
        Long clinicaId = clinicaConfigService.obterClinicaAtual().getId();
        return mensagemService.listarHistorico(id, clinicaId, pageable);
    }

    /**
     * Transfere o atendimento para outro atendente.
     */
    @PostMapping("/{id}/transferir")
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public AtendimentoDetalheDTO transferir(
            @PathVariable Long id,
            @RequestBody @Valid TransferirAtendimentoRequest req) {
        Long clinicaId = clinicaConfigService.obterClinicaAtual().getId();
        return atendimentoService.transferir(id, req, clinicaId);
    }

    /**
     * Encerra o atendimento.
     */
    @PostMapping("/{id}/encerrar")
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public AtendimentoDetalheDTO encerrar(
            @PathVariable Long id,
            @RequestParam(required = false) String motivo) {
        Long clinicaId = clinicaConfigService.obterClinicaAtual().getId();
        return atendimentoService.encerrar(id, clinicaId, motivo);
    }
}
