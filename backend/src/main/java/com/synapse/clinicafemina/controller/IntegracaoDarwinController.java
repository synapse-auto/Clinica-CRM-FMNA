package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.darwin.DarwinAvailableTimetablesResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinInsuranceListResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinLocationRef;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientRecordDTO;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientScheduleResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinProcedureListResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinProfessionalRef;
import com.synapse.clinicafemina.dto.darwin.DarwinProfessionalTimetableDTO;
import com.synapse.clinicafemina.dto.darwin.DarwinStatusResponse;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.DarwinNotAvailableException;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.DarwinConsultaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Consultas Darwin sob demanda para uso interno do CRM.
 * Somente leitura — nenhuma rota de escrita é exposta nesta etapa.
 * Nunca aceita ou retorna DARWIN_API_TOKEN; nunca registra CPF completo em logs.
 */
@RestController
@RequestMapping("/api/integracoes/darwin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GESTOR', 'RECEPCIONISTA')")
public class IntegracaoDarwinController {

    private final ClinicaConfigService clinicaConfigService;
    private final DarwinConsultaService darwinConsultaService;

    @GetMapping("/status")
    public DarwinStatusResponse status() {
        return darwinConsultaService.status();
    }

    @GetMapping("/profissionais")
    public List<DarwinProfessionalRef> profissionais() {
        assertDarwinDisponivel();
        return darwinConsultaService.listarProfissionaisDoLocal();
    }

    @GetMapping("/horarios")
    public DarwinAvailableTimetablesResponse horarios(
            @RequestParam String date,
            @RequestParam(required = false) List<String> professionalIds
    ) {
        assertDarwinDisponivel();
        return darwinConsultaService.listarHorariosDisponiveis(parseData(date, "date"), professionalIds);
    }

    @GetMapping("/grades")
    public List<DarwinProfessionalTimetableDTO> grades(
            @RequestParam(required = false) String professionalId,
            @RequestParam(required = false) String locationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String weekday,
            @RequestParam(required = false) Boolean isOnlineAvailable,
            @RequestParam(required = false) String startDate
    ) {
        assertDarwinDisponivel();
        return darwinConsultaService.listarGradesDoProfissional(
                professionalId, locationId, status, weekday, isOnlineAvailable,
                parseDataOpcional(startDate, "startDate"));
    }

    @GetMapping("/paciente")
    public DarwinPatientRecordDTO paciente(@RequestParam String cpf) {
        assertDarwinDisponivel();
        return darwinConsultaService.buscarPacientePorCpf(cpf);
    }

    @GetMapping("/agendamentos")
    public DarwinPatientScheduleResponse agendamentos(
            @RequestParam String cpf,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String status
    ) {
        assertDarwinDisponivel();
        return darwinConsultaService.listarAgendamentosPorCpf(
                cpf, parseDataOpcional(startDate, "startDate"), parseDataOpcional(endDate, "endDate"), status);
    }

    @GetMapping("/procedimentos")
    public DarwinProcedureListResponse procedimentos(
            @RequestParam(required = false) String locationId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer amount
    ) {
        assertDarwinDisponivel();
        return darwinConsultaService.listarProcedimentos(locationId, name, page, amount);
    }

    @GetMapping("/locais")
    public List<DarwinLocationRef> locais() {
        assertDarwinDisponivel();
        return darwinConsultaService.listarLocaisDoProfissional();
    }

    @GetMapping("/convenios")
    public DarwinInsuranceListResponse convenios(
            @RequestParam(required = false) String locationId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer amount
    ) {
        assertDarwinDisponivel();
        return darwinConsultaService.listarConvenios(locationId, name, page, amount);
    }

    private void assertDarwinDisponivel() {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        if (clinica.getExternalProvider() != ExternalProviderType.DARWIN) {
            throw new DarwinNotAvailableException("A clínica atual não utiliza a integração Darwin.");
        }
        if (!darwinConsultaService.isEnabled()) {
            throw new DarwinNotAvailableException("A integração Darwin não está habilitada.");
        }
    }

    private LocalDate parseData(String valor, String nomeParametro) {
        LocalDate data = parseDataOpcional(valor, nomeParametro);
        if (data == null) {
            throw new BadRequestException("Parametro '" + nomeParametro + "' e obrigatorio.");
        }
        return data;
    }

    private LocalDate parseDataOpcional(String valor, String nomeParametro) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(
                    "Parametro '" + nomeParametro + "' invalido. Use o formato YYYY-MM-DD.");
        }
    }
}
