package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.agenda.AgendaAgendamentoDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaHorarioDisponivelDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaPacienteDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaProfissionalDTO;
import com.synapse.clinicafemina.dto.agenda.AtualizarAgendamentoRequest;
import com.synapse.clinicafemina.dto.agenda.NovoAgendamentoRequest;
import com.synapse.clinicafemina.dto.agenda.NovoPacienteRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.service.AgendaService;
import com.synapse.clinicafemina.service.N8nCallbackAuthorizationService;
import com.synapse.clinicafemina.service.N8nIdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Contrato provider-agnostic da Agenda para o workflow N8N: as mesmas operações de
 * {@link com.synapse.clinicafemina.controller.AgendaController}, autenticadas por
 * X-N8N-SECRET (não por JWT de usuário) e sem exigir sessão de atendimento. O backend
 * resolve o provider (Medware/Darwin) pela clínica do deployment — o workflow externo
 * nunca precisa saber qual integração está por trás. Nenhum token Darwin é aceito
 * aqui; a criação é idempotente para tolerar retries de timeout do N8N.
 */
@Slf4j
@RestController
@RequestMapping("/api/n8n/agenda")
@RequiredArgsConstructor
public class N8nAgendaController {

    private final N8nCallbackAuthorizationService authorizationService;
    private final AgendaService agendaService;
    private final N8nIdempotencyService idempotencyService;

    @GetMapping("/profissionais")
    public List<AgendaProfissionalDTO> profissionais(
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret
    ) {
        Clinica clinica = authorizationService.autorizarClinica(secret);
        return agendaService.listarProfissionais(clinica);
    }

    @GetMapping("/horarios")
    public List<AgendaHorarioDisponivelDTO> horarios(
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret,
            @RequestParam String date,
            @RequestParam(required = false) List<String> professionalId
    ) {
        Clinica clinica = authorizationService.autorizarClinica(secret);
        return agendaService.listarHorariosDisponiveis(clinica, parseData(date), professionalId);
    }

    @GetMapping("/paciente")
    public List<AgendaAgendamentoDTO> agendamentosPorPaciente(
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret,
            @RequestParam String cpf
    ) {
        Clinica clinica = authorizationService.autorizarClinica(secret);
        return agendaService.listarPorPaciente(clinica, cpf);
    }

    @PostMapping("/pacientes")
    @ResponseStatus(HttpStatus.CREATED)
    public AgendaPacienteDTO criarOuLocalizarPaciente(
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret,
            @Valid @RequestBody NovoPacienteRequest request
    ) {
        Clinica clinica = authorizationService.autorizarClinica(secret);
        AgendaPacienteDTO paciente = agendaService.criarOuLocalizarPaciente(clinica, request);
        log.info("Paciente localizado/cadastrado via N8N (agenda). pacienteId={}", paciente.id());
        return paciente;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgendaAgendamentoDTO criar(
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody NovoAgendamentoRequest request
    ) {
        Clinica clinica = authorizationService.autorizarClinica(secret);
        AgendaAgendamentoDTO agendamento = idempotencyService.executarCriacaoIdempotente(
                clinica, N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO, idempotencyKey, request,
                agendaService::criarAgendamento);
        log.info("Agendamento criado (ou reaproveitado por Idempotency-Key) via N8N. idLocal={}",
                agendamento.idLocal());
        return agendamento;
    }

    @PostMapping("/encaixe")
    @ResponseStatus(HttpStatus.CREATED)
    public AgendaAgendamentoDTO criarEncaixe(
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody NovoAgendamentoRequest request
    ) {
        Clinica clinica = authorizationService.autorizarClinica(secret);
        AgendaAgendamentoDTO agendamento = idempotencyService.executarCriacaoIdempotente(
                clinica, N8nIdempotencyService.OPERACAO_CRIAR_ENCAIXE, idempotencyKey, request,
                agendaService::criarEncaixe);
        log.info("Encaixe criado (ou reaproveitado por Idempotency-Key) via N8N. idLocal={}",
                agendamento.idLocal());
        return agendamento;
    }

    @PutMapping("/{id}")
    public AgendaAgendamentoDTO atualizar(
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret,
            @PathVariable Long id,
            @Valid @RequestBody AtualizarAgendamentoRequest request
    ) {
        Clinica clinica = authorizationService.autorizarClinica(secret);
        AgendaAgendamentoDTO agendamento = agendaService.atualizarAgendamento(clinica, id, request);
        log.info("Agendamento reagendado via N8N. idLocal={}", id);
        return agendamento;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret,
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Cancelado via automação") String motivo
    ) {
        Clinica clinica = authorizationService.autorizarClinica(secret);
        agendaService.cancelarAgendamento(clinica, id, motivo);
        log.info("Agendamento cancelado via N8N. idLocal={}", id);
    }

    private LocalDate parseData(String valor) {
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Parametro 'date' invalido. Use o formato YYYY-MM-DD.");
        }
    }
}
