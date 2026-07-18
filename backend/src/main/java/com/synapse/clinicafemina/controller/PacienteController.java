package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.operacional.TagResponse;
import com.synapse.clinicafemina.dto.paciente.PacienteResumoDTO;
import com.synapse.clinicafemina.dto.paciente.PacientePageResponse;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.PacienteService;
import com.synapse.clinicafemina.service.PacienteTagService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de leitura de pacientes.
 * Acesso somente leitura para todos os perfis autenticados da clínica.
 * Criação e edição manual de pacientes não fazem parte do escopo v1
 * — pacientes são populados via integração externa (Medware / Darwin).
 */
@RestController
@RequestMapping("/api/pacientes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
public class PacienteController {

    private final ClinicaConfigService clinicaConfigService;
    private final PacienteService pacienteService;
    private final PacienteTagService pacienteTagService;

    /**
     * GET /api/pacientes
     * Lista todos os pacientes ativos da clínica (sem paginação — volume clínico típico &lt; 5k).
     */
    @GetMapping
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

    /**
     * GET /api/pacientes/{id}
     * Retorna um paciente específico da clínica.
     */
    @GetMapping("/{id}")
    public PacienteResumoDTO buscarPorId(@PathVariable Long id) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return pacienteService.buscarPorId(id, clinica);
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
