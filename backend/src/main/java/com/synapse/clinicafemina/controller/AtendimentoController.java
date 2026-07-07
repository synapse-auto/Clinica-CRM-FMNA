package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.AtendenteOptionDTO;
import com.synapse.clinicafemina.dto.AtendimentoDetalheDTO;
import com.synapse.clinicafemina.dto.AtendimentoResumoDTO;
import com.synapse.clinicafemina.dto.ConvenioReviewRequest;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.dto.atendimento.AtendimentoLembreteRequest;
import com.synapse.clinicafemina.dto.atendimento.AtendimentoLembreteResponse;
import com.synapse.clinicafemina.dto.operacional.TagResponse;
import com.synapse.clinicafemina.domain.MidiaMensagem;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient.MidiaBaixada;
import com.synapse.clinicafemina.service.AtendimentoService;
import com.synapse.clinicafemina.service.AtendimentoLembreteService;
import com.synapse.clinicafemina.service.AtendimentoTagService;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.ConvenioReviewService;
import com.synapse.clinicafemina.service.MensagemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/atendimentos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
public class AtendimentoController {

    private final AtendimentoService atendimentoService;
    private final AtendimentoLembreteService atendimentoLembreteService;
    private final AtendimentoTagService atendimentoTagService;
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

    @GetMapping("/{id}/tags")
    public List<TagResponse> listarTags(@PathVariable Long id) {
        return atendimentoTagService.listar(id, clinicaId());
    }

    @PostMapping("/{id}/tags/{tagId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public List<TagResponse> adicionarTag(@PathVariable Long id, @PathVariable Long tagId) {
        return atendimentoTagService.adicionar(id, tagId, clinicaId());
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public void removerTag(@PathVariable Long id, @PathVariable Long tagId) {
        atendimentoTagService.remover(id, tagId, clinicaId());
    }

    @GetMapping("/{id}/lembretes")
    public List<AtendimentoLembreteResponse> listarLembretes(@PathVariable Long id) {
        return atendimentoLembreteService.listar(id, clinicaId());
    }

    @PostMapping("/{id}/lembretes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public AtendimentoLembreteResponse criarLembrete(
            @PathVariable Long id,
            @RequestBody AtendimentoLembreteRequest request,
            @AuthenticationPrincipal Usuario usuario
    ) {
        return atendimentoLembreteService.criar(id, request, clinicaId(), usuario.getId());
    }

    @PatchMapping("/{id}/lembretes/{lembreteId}/concluir")
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public AtendimentoLembreteResponse concluirLembrete(
            @PathVariable Long id,
            @PathVariable Long lembreteId
    ) {
        return atendimentoLembreteService.concluir(id, lembreteId, clinicaId());
    }

    @PatchMapping("/{id}/lembretes/{lembreteId}/cancelar")
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public AtendimentoLembreteResponse cancelarLembrete(
            @PathVariable Long id,
            @PathVariable Long lembreteId
    ) {
        return atendimentoLembreteService.cancelar(id, lembreteId, clinicaId());
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

    @PatchMapping("/{id}/modo-ia")
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public AtendimentoDetalheDTO ativarModoIa(@PathVariable Long id) {
        return atendimentoService.ativarModoIa(id, clinicaId());
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

    @GetMapping("/{id}/mensagens/{mensagemId}/midia")
    public ResponseEntity<byte[]> obterMidia(
            @PathVariable Long id,
            @PathVariable Long mensagemId
    ) {
        Long clinicaId = clinicaId();
        MidiaMensagem midia = mensagemService.buscarMidia(id, mensagemId, clinicaId);
        
        MidiaBaixada baixada = mensagemService.obterBinarioMidia(midia);
        if (baixada == null || baixada.bytes() == null || baixada.bytes().length == 0) {
            return midiaIndisponivel();
        }
        
        String mime = baixada.mimeType() != null ? baixada.mimeType() : midia.getMimeType();
        
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(mime))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + midia.getNomeArquivo() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store, must-revalidate")
                .body(baixada.bytes());
    }

    private ResponseEntity<byte[]> midiaIndisponivel() {
        byte[] body = """
                {"message":"Mídia indisponível no momento. Tente novamente em instantes."}
                """.trim().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, must-revalidate")
                .body(body);
    }

    private Long clinicaId() {
        return clinicaConfigService.obterClinicaAtual().getId();
    }
}
