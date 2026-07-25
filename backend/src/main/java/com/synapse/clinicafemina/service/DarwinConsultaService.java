package com.synapse.clinicafemina.service;

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
import com.synapse.clinicafemina.exception.DarwinIntegrationException;
import com.synapse.clinicafemina.integration.DarwinClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Consultas Darwin sob demanda (catálogo + agenda + paciente por CPF).
 * Não implementa nem expõe nenhuma operação de escrita (create/update/archive/delete).
 */
@Service
public class DarwinConsultaService {

    private static final int PAGINACAO_MAXIMA = 100;
    private static final Set<String> STATUS_AGENDAMENTO_VALIDOS =
            Set.of("Marcado", "Confirmado", "Aguardando", "Atendido");
    private static final Set<String> STATUS_GRADE_VALIDOS = Set.of("ativa", "arquivada");
    private static final Set<String> DIAS_SEMANA_VALIDOS = Set.of(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");

    private final DarwinClient darwinClient;
    private final boolean enabled;
    private final boolean configured;

    public DarwinConsultaService(
            DarwinClient darwinClient,
            @Value("${app.darwin.enabled:false}") boolean enabled,
            @Value("${app.darwin.api-url:}") String apiUrl,
            @Value("${app.darwin.api-token:}") String apiToken) {
        this.darwinClient = darwinClient;
        this.enabled = enabled;
        this.configured = apiUrl != null && !apiUrl.isBlank() && apiToken != null && !apiToken.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public DarwinStatusResponse status() {
        return new DarwinStatusResponse(
                enabled, "DARWIN", configured, false, true,
                false, true, true, "KNOWN_CRM_PATIENTS_ONLY");
    }

    public List<DarwinProfessionalRef> listarProfissionaisDoLocal() {
        return call(darwinClient::listarProfissionaisDoLocal);
    }

    public DarwinAvailableTimetablesResponse listarHorariosDisponiveis(
            LocalDate date, List<String> professionalIds) {
        if (date == null) {
            throw new BadRequestException("Parametro 'date' e obrigatorio.");
        }
        return call(() -> darwinClient.listarHorariosDisponiveis(date, professionalIds));
    }

    public List<DarwinProfessionalTimetableDTO> listarGradesDoProfissional(
            String professionalId,
            String locationId,
            String status,
            String weekday,
            Boolean isOnlineAvailable,
            LocalDate startDate) {
        validarEnum(status, STATUS_GRADE_VALIDOS, "status");
        validarEnum(weekday, DIAS_SEMANA_VALIDOS, "weekday");
        return call(() -> darwinClient.listarGradesDoProfissional(
                professionalId, locationId, status, weekday, isOnlineAvailable, startDate));
    }

    public DarwinPatientRecordDTO buscarPacientePorCpf(String cpf) {
        String cpfFormatado = validarCpf(cpf);
        return call(() -> darwinClient.buscarPacientePorCpf(cpfFormatado));
    }

    public DarwinPatientScheduleResponse listarAgendamentosPorCpf(
            String cpf, LocalDate startDate, LocalDate endDate, String status) {
        String cpfFormatado = validarCpf(cpf);
        validarEnum(status, STATUS_AGENDAMENTO_VALIDOS, "status");
        return call(() -> darwinClient.listarAgendamentosPorCpf(cpfFormatado, startDate, endDate, status));
    }

    public DarwinProcedureListResponse listarProcedimentos(
            String locationId, String name, Integer page, Integer amount) {
        validarPaginacao(page, amount);
        return call(() -> darwinClient.listarProcedimentos(locationId, name, page, amount));
    }

    public List<DarwinLocationRef> listarLocaisDoProfissional() {
        return call(darwinClient::listarLocaisDoProfissional);
    }

    public DarwinInsuranceListResponse listarConvenios(
            String locationId, String name, Integer page, Integer amount) {
        validarPaginacao(page, amount);
        return call(() -> darwinClient.listarConvenios(locationId, name, page, amount));
    }

    private String validarCpf(String cpf) {
        if (cpf == null) {
            throw new BadRequestException("Parametro 'cpf' e obrigatorio.");
        }
        String digitos = cpf.replaceAll("[^0-9]", "");
        if (digitos.length() != 11) {
            throw new BadRequestException("CPF invalido.");
        }
        return digitos.substring(0, 3) + "." + digitos.substring(3, 6) + "."
                + digitos.substring(6, 9) + "-" + digitos.substring(9, 11);
    }

    private void validarEnum(String valor, Set<String> validos, String nomeParametro) {
        if (valor != null && !validos.contains(valor)) {
            throw new BadRequestException("Valor invalido para o parametro '" + nomeParametro + "'.");
        }
    }

    private void validarPaginacao(Integer page, Integer amount) {
        if (page != null && page < 1) {
            throw new BadRequestException("Parametro 'page' deve ser maior ou igual a 1.");
        }
        if (amount != null && (amount < 1 || amount > PAGINACAO_MAXIMA)) {
            throw new BadRequestException(
                    "Parametro 'amount' deve estar entre 1 e " + PAGINACAO_MAXIMA + ".");
        }
    }

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (RestClientResponseException e) {
            throw mapStatus(e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new DarwinIntegrationException(504, "Tempo limite ao comunicar com a integracao Darwin.");
        } catch (RestClientException e) {
            throw new DarwinIntegrationException(502, "Resposta invalida da integracao Darwin.");
        }
    }

    private DarwinIntegrationException mapStatus(int status) {
        return switch (status) {
            case 400 -> new DarwinIntegrationException(400, "Parametros invalidos para a integracao Darwin.");
            case 401, 403 -> new DarwinIntegrationException(403, "Acesso negado pelo escopo do token Darwin.");
            case 404 -> new DarwinIntegrationException(404, "Recurso nao encontrado na integracao Darwin.");
            case 429 -> new DarwinIntegrationException(429, "Limite de requisicoes da integracao Darwin excedido.");
            default -> new DarwinIntegrationException(502, "Falha ao comunicar com a integracao Darwin.");
        };
    }
}
