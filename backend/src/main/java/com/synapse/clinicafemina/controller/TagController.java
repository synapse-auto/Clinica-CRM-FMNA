package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.operacional.StatusRequest;
import com.synapse.clinicafemina.dto.operacional.TagRequest;
import com.synapse.clinicafemina.dto.operacional.TagResponse;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.TagService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final ClinicaConfigService clinicaConfigService;
    private final TagService tagService;

    @GetMapping
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public List<TagResponse> listar() {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return tagService.listar(clinica);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GESTOR')")
    public TagResponse criar(@RequestBody @Valid TagRequest request) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return tagService.criar(clinica, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('GESTOR')")
    public TagResponse atualizar(@PathVariable Long id, @RequestBody @Valid TagRequest request) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return tagService.atualizar(clinica, id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('GESTOR')")
    public TagResponse alterarStatus(@PathVariable Long id, @RequestBody @Valid StatusRequest request) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return tagService.alterarStatus(clinica, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('GESTOR')")
    public void excluir(@PathVariable Long id) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        tagService.excluir(clinica, id);
    }
}
