package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.agendamento.AgendaOptionsResponse;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoCancelRequest;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoCreateRequest;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoResponse;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoUpdateRequest;
import com.synapse.clinicafemina.service.AgendamentoService;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agendamentos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
public class AgendamentoController {

    private final ClinicaConfigService clinicaConfigService;
    private final AgendamentoService agendamentoService;

    @GetMapping
    public List<AgendamentoResponse> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fim
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendamentoService.listar(clinica, inicio, fim);
    }

    @GetMapping("/opcoes")
    public AgendaOptionsResponse listarOpcoes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime inicio,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fim
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return inicio == null && fim == null
                ? agendamentoService.listarOpcoes(clinica)
                : agendamentoService.listarOpcoes(clinica, inicio, fim);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public AgendamentoResponse criar(@RequestBody @Valid AgendamentoCreateRequest request) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendamentoService.criar(clinica, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public AgendamentoResponse atualizar(
            @PathVariable Long id,
            @RequestBody @Valid AgendamentoUpdateRequest request
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendamentoService.atualizar(clinica, id, request);
    }

    @PatchMapping("/{id}/cancelamento")
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public AgendamentoResponse cancelar(
            @PathVariable Long id,
            @RequestBody @Valid AgendamentoCancelRequest request
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendamentoService.cancelar(clinica, id, request);
    }
}
