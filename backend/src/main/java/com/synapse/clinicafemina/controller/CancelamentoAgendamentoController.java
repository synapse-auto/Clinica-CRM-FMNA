package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.cancelamento.CancelamentoAgendamentoResponse;
import com.synapse.clinicafemina.service.CancelamentoAgendamentoService;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/cancelamentos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA', 'MEDICO')")
public class CancelamentoAgendamentoController {
    private final ClinicaConfigService clinicaConfigService;
    private final CancelamentoAgendamentoService service;

    @GetMapping
    public Page<CancelamentoAgendamentoResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String origem,
            @RequestParam(required = false) String statusCancelamento,
            @RequestParam(required = false) String statusSincronizacao,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long agendamentoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fim,
            @PageableDefault(size = 20, sort = "coletadoEm") Pageable pageable
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return service.listar(clinica, busca, origem, statusCancelamento, statusSincronizacao, pacienteId, agendamentoId, inicio, fim, pageable);
    }
}
