package com.synapse.clinicafemina.integration.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class MedwareProvider implements ExternalClinicProvider {

    private static final ZoneId MEDWARE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int DEFAULT_TOKEN_LIFETIME_HOURS = 24;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MedwareApiMapper mapper;
    private final Clock clock;
    private final String apiUrl;
    private final String username;
    private final String password;
    private final boolean passwordIsHash;
    private final long tokenRefreshMarginSeconds;
    private final int defaultStartDaysBack;
    private final int defaultEndDaysForward;
    private final AtomicReference<TokenState> tokenState = new AtomicReference<>();

    @Autowired
    public MedwareProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            MedwareApiMapper mapper,
            @Value("${app.medware.api-url:}") String apiUrl,
            @Value("${app.medware.username:}") String username,
            @Value("${app.medware.password:}") String password,
            @Value("${app.medware.password-is-hash:false}") boolean passwordIsHash,
            @Value("${app.medware.token-refresh-margin-seconds:300}") long tokenRefreshMarginSeconds,
            @Value("${app.medware.timeout-seconds:30}") int timeoutSeconds,
            @Value("${app.medware.default-start-days-back:30}") int defaultStartDaysBack,
            @Value("${app.medware.default-end-days-forward:60}") int defaultEndDaysForward) {
        this(
                withTimeout(restClientBuilder, timeoutSeconds),
                objectMapper,
                mapper,
                Clock.system(MEDWARE_ZONE),
                apiUrl,
                username,
                password,
                passwordIsHash,
                tokenRefreshMarginSeconds,
                timeoutSeconds,
                defaultStartDaysBack,
                defaultEndDaysForward
        );
    }

    MedwareProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            MedwareApiMapper mapper,
            Clock clock,
            String apiUrl,
            String username,
            String password,
            boolean passwordIsHash,
            long tokenRefreshMarginSeconds,
            int timeoutSeconds,
            int defaultStartDaysBack,
            int defaultEndDaysForward) {
        this.objectMapper = objectMapper;
        this.mapper = mapper;
        this.clock = clock;
        this.apiUrl = trimTrailingSlash(apiUrl);
        this.username = username;
        this.password = password;
        this.passwordIsHash = passwordIsHash;
        this.tokenRefreshMarginSeconds = tokenRefreshMarginSeconds;
        this.defaultStartDaysBack = defaultStartDaysBack;
        this.defaultEndDaysForward = defaultEndDaysForward;
        this.restClient = restClientBuilder
                .baseUrl(this.apiUrl == null || this.apiUrl.isBlank() ? "http://medware.invalid" : this.apiUrl)
                .build();
    }

    private static RestClient.Builder withTimeout(RestClient.Builder builder, int timeoutSeconds) {
        int safeTimeout = Math.max(1, timeoutSeconds);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(safeTimeout).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(safeTimeout).toMillis());
        return builder.requestFactory(requestFactory);
    }

    @Override
    public ExternalProviderType getType() {
        return ExternalProviderType.MEDWARE;
    }

    @Override
    public PageResult<ExternalPatientDTO> getPatients(OffsetDateTime updatedAfter, String cursor, int limit) {
        assertConfigured();
        JsonNode response = get("/Medware/Paciente/Listar", Map.of());
        List<ExternalPatientDTO> patients = mapper.extractItems(response).stream()
                .map(mapper::toPatient)
                .filter(patient -> patient.externalId() != null)
                .toList();
        return page(patients, cursor, limit);
    }

    @Override
    public PageResult<ExternalAppointmentDTO> getAppointments(OffsetDateTime updatedAfter, String cursor, int limit) {
        assertConfigured();
        DateWindow dateWindow = dateWindow(updatedAfter);
        Map<String, String> dateParams = dateParams(dateWindow);
        Map<String, JsonNode> procedimentos = loadCatalog(
                "/Medware/ProcedPlanoOp/Listar",
                dateParams,
                "codProcedimento",
                "codprocedimento"
        );
        Map<String, JsonNode> medicos = loadCatalog(
                "/Medware/Medico/Listar",
                dateParams,
                "codMedico",
                "codmedico"
        );
        JsonNode response = get("/Medware/Agendamento/Listar", dateParams);
        List<ExternalAppointmentDTO> appointments = mapper.extractItems(response).stream()
                .map(node -> mapper.toAppointment(node, procedimentos, medicos))
                .filter(appointment -> appointment.externalId() != null && appointment.externalPatientId() != null)
                .toList();
        return page(appointments, cursor, limit);
    }

    @Override
    public PageResult<ExternalClinicalNoteDTO> getPatientNotes(String externalPatientId, String cursor, int limit) {
        return new PageResult<>(List.of(), false, null);
    }

    private Map<String, JsonNode> loadCatalog(String path, Map<String, String> params, String... indexFields) {
        try {
            return mapper.indexBy(get(path, params), indexFields);
        } catch (Exception e) {
            log.warn("Falha ao ler catalogo Medware path={}, tipoErro={}", path, e.getClass().getSimpleName());
            return Map.of();
        }
    }

    private JsonNode get(String path, Map<String, String> queryParams) {
        String uri = uri(path, queryParams);
        String body = restClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + bearerToken())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 401) {
                        tokenState.set(null);
                    }
                    throw new IllegalStateException("Medware retornou status " + status + " em endpoint read-only " + path);
                })
                .body(String.class);
        return readJson(body, path);
    }

    private String bearerToken() {
        TokenState current = tokenState.get();
        if (current != null && !current.shouldRefresh(clock, tokenRefreshMarginSeconds)) {
            return current.token();
        }
        synchronized (tokenState) {
            current = tokenState.get();
            if (current != null && !current.shouldRefresh(clock, tokenRefreshMarginSeconds)) {
                return current.token();
            }
            TokenState refreshed = current != null && current.refreshToken() != null
                    ? tryRefresh(current)
                    : null;
            if (refreshed == null) {
                refreshed = login();
            }
            tokenState.set(refreshed);
            return refreshed.token();
        }
    }

    private TokenState tryRefresh(TokenState current) {
        try {
            String response = restClient.post()
                    .uri(uri("/Acesso/refreshToken", Map.of(
                            "refreshToken", current.refreshToken(),
                            "token", current.token()
                    )))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            return parseToken(response);
        } catch (Exception e) {
            log.warn("Falha ao renovar token Medware; novo login sera realizado. tipoErro={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private TokenState login() {
        String response = restClient.post()
                .uri("/Acesso/login")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "identificacao", username,
                        "senha", password,
                        "isHash", passwordIsHash
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, result) -> {
                    throw new IllegalStateException("Falha de autenticacao Medware. Status " + result.getStatusCode().value());
                })
                .body(String.class);
        return parseToken(response);
    }

    private JsonNode readJson(String body, String path) {
        if (body == null || body.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Resposta Medware invalida em endpoint read-only " + path);
        }
    }

    private TokenState parseToken(String body) {
        String token = null;
        String refreshToken = null;
        if (body != null && !body.isBlank()) {
            try {
                JsonNode response = objectMapper.readTree(body);
                if (response.isTextual()) {
                    token = response.asText();
                } else if (response.isObject()) {
                    token = text(response, "token", "accessToken", "jwt");
                    refreshToken = text(response, "refreshToken", "refresh_token");
                }
            } catch (Exception e) {
                token = body.trim();
            }
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Resposta de autenticacao Medware nao retornou token");
        }
        return new TokenState(token, refreshToken, OffsetDateTime.now(clock).plusHours(DEFAULT_TOKEN_LIFETIME_HOURS));
    }

    private DateWindow dateWindow(OffsetDateTime updatedAfter) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = updatedAfter == null
                ? today.minusDays(defaultStartDaysBack)
                : updatedAfter.atZoneSameInstant(MEDWARE_ZONE).toLocalDate();
        LocalDate end = today.plusDays(defaultEndDaysForward);
        if (end.isBefore(start)) {
            end = start;
        }
        return new DateWindow(start, end);
    }

    private Map<String, String> dateParams(DateWindow dateWindow) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("dataInicio", BR_DATE.format(dateWindow.start()));
        params.put("dataFim", BR_DATE.format(dateWindow.end()));
        return params;
    }

    private <T> PageResult<T> page(List<T> items, String cursor, int limit) {
        int safeLimit = limit <= 0 ? items.size() : limit;
        int offset = parseCursor(cursor);
        int end = Math.min(offset + safeLimit, items.size());
        List<T> data = offset >= items.size() ? List.of() : items.subList(offset, end);
        boolean hasMore = end < items.size();
        return new PageResult<>(data, hasMore, hasMore ? String.valueOf(end) : null);
    }

    private int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(cursor));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String uri(String path, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return path;
        }
        StringBuilder builder = new StringBuilder(path).append("?");
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            if (!first) {
                builder.append("&");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return builder.toString();
    }

    private void assertConfigured() {
        if (apiUrl == null || apiUrl.isBlank() || username == null || username.isBlank()
                || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "MEDWARE_API_URL, MEDWARE_USERNAME e MEDWARE_PASSWORD devem ser configuradas para habilitar o provider Medware"
            );
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.replaceAll("/+$", "");
    }

    private String text(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private record DateWindow(LocalDate start, LocalDate end) {
    }

    private record TokenState(String token, String refreshToken, OffsetDateTime expiresAt) {
        private boolean shouldRefresh(Clock clock, long marginSeconds) {
            return expiresAt.minusSeconds(Math.max(0, marginSeconds)).isBefore(OffsetDateTime.now(clock));
        }
    }
}
