package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.agenda.AgendaAgendamentoDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaCapabilitiesDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaConvenioDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaHorarioDisponivelDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaLocalDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaPacienteDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaProcedimentoDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaProfissionalDTO;
import com.synapse.clinicafemina.dto.agenda.AtualizarAgendamentoRequest;
import com.synapse.clinicafemina.dto.agenda.NovoAgendamentoRequest;
import com.synapse.clinicafemina.dto.agenda.NovoPacienteRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.service.AgendaService;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.CancelamentoAgendamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Agenda normalizada, provider-agnostic: o frontend/N8N consomem este contrato sem
 * precisar conhecer Medware ou Darwin. O backend resolve o provider pela clínica
 * autenticada. Reaproveita {@code /api/agendamentos} para Medware por baixo dos panos
 * (via {@link AgendaService}) — não substitui aquele endpoint, que continua em uso
 * pela tela atual da UltraMedical.
 */
@RestController
@RequestMapping("/api/agenda")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
public class AgendaController {

    private final ClinicaConfigService clinicaConfigService;
    private final AgendaService agendaService;
    private final CancelamentoAgendamentoService cancelamentoAgendamentoService;

    @GetMapping("/capabilities")
    public AgendaCapabilitiesDTO capabilities() {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendaService.capacidades(clinica);
    }

    @GetMapping
    public List<AgendaAgendamentoDTO> listar(
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendaService.listarAgenda(clinica, parseDataHora(startDate, "startDate"), parseDataHora(endDate, "endDate"));
    }

    @GetMapping("/{id}")
    public AgendaAgendamentoDTO buscarPorId(@PathVariable Long id) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendaService.buscarPorId(clinica, id);
    }

    @GetMapping("/paciente")
    public List<AgendaAgendamentoDTO> listarPorPaciente(@RequestParam String cpf) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendaService.listarPorPaciente(clinica, cpf);
    }

    @GetMapping("/profissionais")
    public List<AgendaProfissionalDTO> profissionais() {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendaService.listarProfissionais(clinica);
    }

    @GetMapping("/horarios")
    public List<AgendaHorarioDisponivelDTO> horarios(
            @RequestParam String date,
            @RequestParam(required = false) List<String> professionalId
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendaService.listarHorariosDisponiveis(clinica, parseData(date, "date"), professionalId);
    }

    @GetMapping("/procedimentos")
    public List<AgendaProcedimentoDTO> procedimentos(@RequestParam(required = false) String localId) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendaService.listarProcedimentos(clinica, localId);
    }

    @GetMapping("/convenios")
    public List<AgendaConvenioDTO> convenios(@RequestParam(required = false) String localId) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendaService.listarConvenios(clinica, localId);
    }

    @GetMapping("/locais")
    public List<AgendaLocalDTO> locais() {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendaService.listarLocais(clinica);
    }

    @PostMapping("/pacientes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public AgendaPacienteDTO criarOuLocalizarPaciente(@Valid @RequestBody NovoPacienteRequest request) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendaService.criarOuLocalizarPaciente(clinica, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public AgendaAgendamentoDTO criar(@Valid @RequestBody NovoAgendamentoRequest request) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendaService.criarAgendamento(clinica, request);
    }

    @PostMapping("/encaixe")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public AgendaAgendamentoDTO criarEncaixe(@Valid @RequestBody NovoAgendamentoRequest request) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendaService.criarEncaixe(clinica, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public AgendaAgendamentoDTO atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarAgendamentoRequest request
    ) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return agendaService.atualizarAgendamento(clinica, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
    public void cancelar(@PathVariable Long id, @RequestParam(required = false, defaultValue = "Cancelado pelo CRM") String motivo) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        agendaService.cancelarAgendamento(clinica, id, motivo);
        cancelamentoAgendamentoService.registrarCancelamentoManual(clinica, id, motivo);
    }

    private LocalDate parseData(String valor, String nomeParametro) {
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Parametro '" + nomeParametro + "' invalido. Use o formato YYYY-MM-DD.");
        }
    }

    private OffsetDateTime parseDataHora(String valor, String nomeParametro) {
        try {
            return OffsetDateTime.parse(valor);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Parametro '" + nomeParametro + "' invalido.");
        }
    }
}
