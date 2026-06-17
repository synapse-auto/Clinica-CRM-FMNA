package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.followup.FollowUpTemporaryRequest;
import com.synapse.clinicafemina.dto.followup.FollowUpTemporaryResponse;
import com.synapse.clinicafemina.dto.followup.FollowUpTemporaryStatusRequest;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.FollowUpTemporaryService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FollowUpTemporaryController {

    private final ClinicaConfigService clinicaConfigService;
    private final FollowUpTemporaryService service;

    @GetMapping("/follow-ups-temporary")
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public Page<FollowUpTemporaryResponse> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Pageable pageable) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return service.listar(clinica, status, pacienteId, from, to, pageable);
    }

    @GetMapping("/pacientes/{pacienteId}/follow-ups-temporary")
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public Page<FollowUpTemporaryResponse> listarPorPaciente(@PathVariable Long pacienteId, Pageable pageable) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return service.listarPorPaciente(clinica, pacienteId, pageable);
    }

    @PostMapping("/pacientes/{pacienteId}/follow-ups-temporary")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public FollowUpTemporaryResponse criar(@PathVariable Long pacienteId,
                                           @RequestBody @Valid FollowUpTemporaryRequest request) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return service.criar(clinica, pacienteId, request);
    }

    @PatchMapping("/follow-ups-temporary/{id}/status")
    @PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
    public FollowUpTemporaryResponse alterarStatus(@PathVariable Long id,
                                                   @RequestBody @Valid FollowUpTemporaryStatusRequest request) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return service.alterarStatus(clinica, id, request);
    }
}
