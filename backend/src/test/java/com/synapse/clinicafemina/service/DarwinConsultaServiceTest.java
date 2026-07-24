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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DarwinConsultaService — validação sob demanda + mapeamento sanitizado de erros")
class DarwinConsultaServiceTest {

    @Mock
    private DarwinClient darwinClient;

    private DarwinConsultaService service;

    @BeforeEach
    void setUp() {
        service = new DarwinConsultaService(darwinClient, true, "https://darwin.test", "token-123");
    }

    @Test
    @DisplayName("status() reflete enabled/configured sem chamar a API externa")
    void status_reflectsFlags_withoutExternalCall() {
        DarwinStatusResponse status = service.status();

        assertThat(status.enabled()).isTrue();
        assertThat(status.provider()).isEqualTo("DARWIN");
        assertThat(status.configured()).isTrue();
        assertThat(status.bulkSyncSupported()).isFalse();
        assertThat(status.onDemandQueriesSupported()).isTrue();
        verifyNoInteractions(darwinClient);
    }

    @Test
    @DisplayName("status() reporta configured=false quando api-url/api-token ausentes")
    void status_reportsNotConfigured_whenCredentialsMissing() {
        DarwinConsultaService semCredenciais = new DarwinConsultaService(darwinClient, false, "", "");

        assertThat(semCredenciais.status().configured()).isFalse();
        assertThat(semCredenciais.status().enabled()).isFalse();
    }

    @Test
    @DisplayName("listarProfissionaisDoLocal delega ao DarwinClient")
    void listarProfissionaisDoLocal_delegatesToClient() {
        when(darwinClient.listarProfissionaisDoLocal())
                .thenReturn(List.of(new DarwinProfessionalRef("prof-1", "Dra. Fulana")));

        List<DarwinProfessionalRef> result = service.listarProfissionaisDoLocal();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("listarHorariosDisponiveis rejeita data nula antes de chamar o client")
    void listarHorariosDisponiveis_rejectsNullDate() {
        assertThatThrownBy(() -> service.listarHorariosDisponiveis(null, null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(darwinClient);
    }

    @Test
    @DisplayName("listarHorariosDisponiveis delega ao client quando a data é valida")
    void listarHorariosDisponiveis_delegatesToClient() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        DarwinAvailableTimetablesResponse resposta = new DarwinAvailableTimetablesResponse(null, List.of());
        when(darwinClient.listarHorariosDisponiveis(date, null)).thenReturn(resposta);

        DarwinAvailableTimetablesResponse result = service.listarHorariosDisponiveis(date, null);

        assertThat(result).isSameAs(resposta);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalida", "arquivadaX", "qualquercoisa"})
    @DisplayName("listarGradesDoProfissional rejeita status fora do enum documentado")
    void listarGradesDoProfissional_rejectsInvalidStatus(String status) {
        assertThatThrownBy(() -> service.listarGradesDoProfissional(
                "prof-1", null, status, null, null, null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(darwinClient);
    }

    @Test
    @DisplayName("listarGradesDoProfissional rejeita weekday fora do enum documentado")
    void listarGradesDoProfissional_rejectsInvalidWeekday() {
        assertThatThrownBy(() -> service.listarGradesDoProfissional(
                "prof-1", null, "ativa", "someday", null, null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(darwinClient);
    }

    @Test
    @DisplayName("listarGradesDoProfissional aceita status/weekday validos e delega")
    void listarGradesDoProfissional_delegatesWithValidEnums() {
        when(darwinClient.listarGradesDoProfissional("prof-1", null, "ativa", "monday", true, null))
                .thenReturn(List.of());

        List<DarwinProfessionalTimetableDTO> result = service.listarGradesDoProfissional(
                "prof-1", null, "ativa", "monday", true, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("buscarPacientePorCpf rejeita CPF com quantidade de digitos invalida")
    void buscarPacientePorCpf_rejectsInvalidCpf() {
        assertThatThrownBy(() -> service.buscarPacientePorCpf("123"))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(darwinClient);
    }

    @Test
    @DisplayName("buscarPacientePorCpf normaliza digitos e formata antes de chamar o client")
    void buscarPacientePorCpf_normalizesAndFormatsCpf() {
        when(darwinClient.buscarPacientePorCpf("000.000.000-00"))
                .thenReturn(new DarwinPatientRecordDTO(
                        "000.000.000-00", "Paciente Teste", null, null, null, null, null,
                        null, null, null, null, null, null, null, List.of(), null));

        DarwinPatientRecordDTO result = service.buscarPacientePorCpf("00000000000");

        assertThat(result.name()).isEqualTo("Paciente Teste");
        verify(darwinClient).buscarPacientePorCpf("000.000.000-00");
    }

    @Test
    @DisplayName("listarAgendamentosPorCpf rejeita status fora do enum documentado")
    void listarAgendamentosPorCpf_rejectsInvalidStatus() {
        assertThatThrownBy(() -> service.listarAgendamentosPorCpf(
                "000.000.000-00", null, null, "StatusInexistente"))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(darwinClient);
    }

    @Test
    @DisplayName("listarAgendamentosPorCpf delega com cpf normalizado e status valido")
    void listarAgendamentosPorCpf_delegatesWithValidStatus() {
        when(darwinClient.listarAgendamentosPorCpf(eq("000.000.000-00"), isNull(), isNull(), eq("Marcado")))
                .thenReturn(new DarwinPatientScheduleResponse(null, List.of(), List.of()));

        DarwinPatientScheduleResponse result =
                service.listarAgendamentosPorCpf("000.000.000-00", null, null, "Marcado");

        assertThat(result.schedules()).isEmpty();
    }

    @Test
    @DisplayName("listarProcedimentos rejeita page menor que 1")
    void listarProcedimentos_rejectsPageBelowMinimum() {
        assertThatThrownBy(() -> service.listarProcedimentos(null, null, 0, 20))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(darwinClient);
    }

    @Test
    @DisplayName("listarProcedimentos rejeita amount acima do maximo documentado (100)")
    void listarProcedimentos_rejectsAmountAboveMaximum() {
        assertThatThrownBy(() -> service.listarProcedimentos(null, null, 1, 101))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(darwinClient);
    }

    @Test
    @DisplayName("listarConvenios aceita paginacao dentro dos limites e delega")
    void listarConvenios_delegatesWithinPaginationLimits() {
        when(darwinClient.listarConvenios(null, null, 1, 100))
                .thenReturn(new DarwinInsuranceListResponse(List.of(), null));

        DarwinInsuranceListResponse result = service.listarConvenios(null, null, 1, 100);

        assertThat(result.insurances()).isEmpty();
    }

    @Test
    @DisplayName("listarLocaisDoProfissional delega ao client")
    void listarLocaisDoProfissional_delegatesToClient() {
        when(darwinClient.listarLocaisDoProfissional())
                .thenReturn(List.of(new DarwinLocationRef("loc-1", "Unidade Centro")));

        List<DarwinLocationRef> result = service.listarLocaisDoProfissional();

        assertThat(result).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 429, 500})
    @DisplayName("erros HTTP da API Darwin sao mapeados para DarwinIntegrationException sanitizada")
    void httpErrors_areMappedToSanitizedException(int status) {
        HttpStatus httpStatus = HttpStatus.valueOf(status);
        Exception erroOrigem = status >= 500
                ? HttpServerErrorException.create(httpStatus, "erro", HttpHeaders.EMPTY, new byte[0], null)
                : HttpClientErrorException.create(httpStatus, "erro", HttpHeaders.EMPTY, new byte[0], null);
        when(darwinClient.listarProfissionaisDoLocal()).thenThrow(erroOrigem);

        assertThatThrownBy(() -> service.listarProfissionaisDoLocal())
                .isInstanceOf(DarwinIntegrationException.class)
                .extracting(ex -> ((DarwinIntegrationException) ex).getMessage())
                .satisfies(message -> assertThat((String) message).doesNotContain("erro"));
    }

    @Test
    @DisplayName("timeout de conexao propaga como DarwinIntegrationException 504 sanitizada")
    void timeout_isMappedToSanitized504() {
        when(darwinClient.listarProfissionaisDoLocal())
                .thenThrow(new ResourceAccessException("timeout simulado"));

        assertThatThrownBy(() -> service.listarProfissionaisDoLocal())
                .isInstanceOf(DarwinIntegrationException.class)
                .satisfies(ex -> assertThat(((DarwinIntegrationException) ex).upstreamStatus()).isEqualTo(504));
    }

    @Test
    @DisplayName("JSON invalido (RestClientException generica) propaga sanitizado como 502")
    void invalidJson_isMappedToSanitized502() {
        when(darwinClient.listarProfissionaisDoLocal())
                .thenThrow(new RestClientException("json invalido simulado"));

        assertThatThrownBy(() -> service.listarProfissionaisDoLocal())
                .isInstanceOf(DarwinIntegrationException.class)
                .satisfies(ex -> assertThat(((DarwinIntegrationException) ex).upstreamStatus()).isEqualTo(502));
    }

    @Test
    @DisplayName("contrato read-only: nenhum metodo de escrita e exposto pelo servico")
    void service_hasNoWriteMethods() {
        List<String> metodosPublicos = java.util.Arrays.stream(DarwinConsultaService.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertThat(metodosPublicos).noneMatch(nome -> {
            String n = nome.toLowerCase(java.util.Locale.ROOT);
            return n.startsWith("create") || n.startsWith("update") || n.startsWith("archive")
                    || n.startsWith("delete") || n.startsWith("save") || n.startsWith("post")
                    || n.startsWith("put") || n.startsWith("patch") || n.startsWith("remove")
                    || n.startsWith("write") || n.startsWith("upload");
        });
    }
}
