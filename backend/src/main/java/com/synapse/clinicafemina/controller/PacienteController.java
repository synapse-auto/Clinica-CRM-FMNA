package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.operacional.TagResponse;
import com.synapse.clinicafemina.dto.paciente.importacao.ImportacaoCsvContatoMapping;
import com.synapse.clinicafemina.dto.paciente.importacao.ImportacaoCsvContatoPreview;
import com.synapse.clinicafemina.dto.paciente.importacao.ImportacaoCsvContatoResultado;
import com.synapse.clinicafemina.dto.paciente.PacienteResumoDTO;
import com.synapse.clinicafemina.dto.paciente.PacientePageResponse;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.PacienteService;
import com.synapse.clinicafemina.service.PacienteFotoPerfilService;
import com.synapse.clinicafemina.service.PacienteTagService;
import com.synapse.clinicafemina.service.ImportacaoCsvContatoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/**
 * Endpoints de leitura de pacientes e importação CSV controlada.
 * A criação e edição manual continuam fora do escopo; a importação CSV é disponível
 * somente para Gestor e Recepcionista, sempre dentro da clínica resolvida no servidor.
 */
@RestController
@RequestMapping("/api/pacientes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
@Tag(name = "Pacientes", description = "Consultas e importação CSV restritas à clínica autenticada.")
@SecurityRequirement(name = "bearerAuth")
public class PacienteController {

    private final ClinicaConfigService clinicaConfigService;
    private final PacienteService pacienteService;
    private final PacienteTagService pacienteTagService;
    private final PacienteFotoPerfilService pacienteFotoPerfilService;
    private final ImportacaoCsvContatoService importacaoCsvContatoService;

    /**
     * GET /api/pacientes
     * Lista todos os pacientes ativos da clínica (sem paginação — volume clínico típico &lt; 5k).
     */
    @GetMapping
    @Operation(summary = "Listar pacientes")
    public List<PacienteResumoDTO> listar() {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return pacienteService.listar(clinica);
    }

    @GetMapping("/pesquisa")
    public PacientePageResponse pesquisar(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long tag
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return pacienteService.pesquisar(clinica, q, page, size, status, tag);
    }

    @PostMapping(value = "/importacoes/csv/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Pré-visualizar importação CSV", description = "multipart/form-data com file e mapping opcional; aceita CSV UTF-8 (com ou sem BOM) ou Windows-1252, separado por vírgula ou ponto e vírgula, de até 5 MB. Devolve amostra, hash e linhas inválidas sem persistir.")
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public ImportacaoCsvContatoPreview previewImportacaoCsv(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "mapping", required = false) ImportacaoCsvContatoMapping mapping,
            @AuthenticationPrincipal Usuario usuario
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return importacaoCsvContatoService.preview(file, mapping, clinica, usuario == null ? null : usuario.getId());
    }

    @PostMapping(value = "/importacoes/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Confirmar importação CSV", description = "multipart/form-data com file, expectedFileHash e mapping. Requer os dados validados no preview; os erros de linha retornam sem expor valores de nome ou telefone.")
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportacaoCsvContatoResultado importarCsv(
            @RequestPart("file") MultipartFile file,
            @RequestPart("expectedFileHash") String expectedFileHash,
            @RequestPart("mapping") ImportacaoCsvContatoMapping mapping,
            @AuthenticationPrincipal Usuario usuario
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return importacaoCsvContatoService.importar(
                file, expectedFileHash, mapping, clinica, usuario == null ? null : usuario.getId()
        );
    }

    /**
     * GET /api/pacientes/{id}
     * Retorna um paciente específico da clínica.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Consultar paciente")
    public PacienteResumoDTO buscarPorId(@PathVariable Long id) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return pacienteService.buscarPorId(id, clinica);
    }

    @GetMapping("/{id}/foto")
    public ResponseEntity<byte[]> obterFoto(
            @PathVariable Long id,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        PacienteFotoPerfilService.FotoArmazenada foto =
                pacienteFotoPerfilService.obter(id, clinica.getId());
        String etag = "\"" + foto.sha256() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(foto.sha256())
                    .cacheControl(CacheControl.noCache().cachePrivate())
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.contentType()))
                .contentLength(foto.conteudo().length)
                .eTag(foto.sha256())
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(foto.conteudo());
    }

    @GetMapping("/{id}/tags")
    public List<TagResponse> listarTags(@PathVariable Long id) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return pacienteTagService.listar(id, clinica.getId());
    }

    @PostMapping("/{id}/tags/{tagId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public List<TagResponse> adicionarTag(@PathVariable Long id, @PathVariable Long tagId) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return pacienteTagService.adicionar(id, tagId, clinica.getId());
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public void removerTag(@PathVariable Long id, @PathVariable Long tagId) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        pacienteTagService.remover(id, tagId, clinica.getId());
    }
}
