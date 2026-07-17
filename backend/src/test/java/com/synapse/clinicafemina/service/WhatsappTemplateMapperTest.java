package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.dto.EnviarTemplateWhatsappRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.WhatsappTemplateParametersException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsappTemplateMapperTest {

    private final WhatsappTemplateMapper mapper = new WhatsappTemplateMapper(
            new WhatsappTemplateParameterMapper()
    );

    @Test
    void should_map_supported_text_template_and_render_safe_preview() {
        var definition = mapper.map(supportedTemplate("APPROVED"));

        var prepared = mapper.prepare(definition, List.of(
                parameter("HEADER", 1, null, "Paciente"),
                parameter("BODY", 1, null, "16/07/2026"),
                parameter("BUTTON", 1, 0, "confirmar-123")
        ));

        assertTrue(definition.dto().suportado());
        assertEquals(3, definition.dto().variaveis().size());
        assertTrue(prepared.preview().contains("Paciente"));
        assertTrue(prepared.preview().contains("16/07/2026"));
        assertEquals(3, prepared.metaComponents().size());
        assertTrue(prepared.metaComponents().stream()
                .flatMap(component -> parameters(component).stream())
                .noneMatch(parameter -> parameter.containsKey("parameter_name")));
    }

    @Test
    void should_support_static_body_without_meta_components() {
        Map<String, Object> raw = Map.of(
                "id", "tpl-2",
                "name", "aviso_estatico",
                "language", "pt_BR",
                "status", "APPROVED",
                "category", "UTILITY",
                "components", List.of(Map.of("type", "BODY", "text", "Aviso confirmado"))
        );

        var prepared = mapper.prepare(mapper.map(raw), List.of());

        assertEquals("Aviso confirmado", prepared.preview());
        assertTrue(prepared.metaComponents().isEmpty());
    }

    @Test
    void should_keep_static_buttons_without_creating_parameters() {
        Map<String, Object> raw = Map.of(
                "id", "tpl-static-button",
                "name", "aviso_com_botao",
                "language", "pt_BR",
                "status", "APPROVED",
                "category", "UTILITY",
                "components", List.of(
                        Map.of("type", "BODY", "text", "Aviso confirmado"),
                        Map.of("type", "BUTTONS", "buttons", List.of(
                                Map.of("type", "URL", "text", "Abrir", "url", "https://example.test/aviso")
                        ))
                )
        );

        var definition = mapper.map(raw);
        var prepared = mapper.prepare(definition, List.of());

        assertTrue(definition.dto().variaveis().isEmpty());
        assertTrue(prepared.metaComponents().isEmpty());
    }

    @Test
    void should_detect_named_header_and_body_and_build_named_payload_and_preview() {
        var definition = mapper.map(namedTemplate());

        assertTrue(definition.dto().suportado());
        assertEquals(2, definition.dto().variaveis().size());
        assertTrue(definition.dto().variaveis().stream()
                .anyMatch(variable -> "HEADER".equals(variable.componente())
                        && "vr_titulo".equals(variable.nomeParametro())));
        assertTrue(definition.dto().variaveis().stream()
                .anyMatch(variable -> "BODY".equals(variable.componente())
                        && "vr_nome".equals(variable.nomeParametro())));

        var prepared = mapper.prepare(definition, List.of(
                namedParameter("HEADER", 1, null, "vr_titulo", "Confirmacao"),
                namedParameter("BODY", 1, null, "vr_nome", "Pessoa ficticia")
        ));

        assertTrue(prepared.preview().contains("Confirmacao"));
        assertTrue(prepared.preview().contains("Pessoa ficticia"));
        List<Map<String, Object>> parameters = prepared.metaComponents().stream()
                .flatMap(component -> parameters(component).stream())
                .toList();
        assertTrue(parameters.stream()
                .anyMatch(parameter -> "vr_titulo".equals(parameter.get("parameter_name"))));
        assertTrue(parameters.stream()
                .anyMatch(parameter -> "vr_nome".equals(parameter.get("parameter_name"))));
    }

    @Test
    void should_infer_named_format_when_meta_omits_parameter_format() {
        Map<String, Object> raw = new LinkedHashMap<>(namedTemplate());
        raw.remove("parameter_format");

        var result = mapper.map(raw).dto();

        assertTrue(result.suportado());
        assertTrue(result.variaveis().stream()
                .anyMatch(variable -> "vr_nome".equals(variable.nomeParametro())));
    }

    @Test
    void should_reject_missing_extra_or_divergent_named_parameters() {
        var definition = mapper.map(namedTemplate());
        var header = namedParameter("HEADER", 1, null, "vr_titulo", "Confirmacao");
        var body = namedParameter("BODY", 1, null, "vr_nome", "Pessoa ficticia");

        assertThrows(WhatsappTemplateParametersException.class,
                () -> mapper.prepare(definition, List.of(header)));
        assertThrows(WhatsappTemplateParametersException.class,
                () -> mapper.prepare(definition, List.of(header, body,
                        namedParameter("BODY", 2, null, "vr_data", "17/07/2026"))));
        assertThrows(WhatsappTemplateParametersException.class,
                () -> mapper.prepare(definition, List.of(header,
                        namedParameter("BODY", 1, null, "nome_divergente", "Pessoa ficticia"))));
    }

    @Test
    void should_mark_mixed_or_incomplete_placeholders_as_unsupported() {
        Map<String, Object> mixed = Map.of(
                "id", "tpl-mixed",
                "name", "misturado",
                "language", "pt_BR",
                "status", "APPROVED",
                "category", "UTILITY",
                "components", List.of(Map.of("type", "BODY", "text", "Ola {{1}} {{vr_nome}}"))
        );
        Map<String, Object> incomplete = Map.of(
                "id", "tpl-incomplete",
                "name", "incompleto",
                "language", "pt_BR",
                "status", "APPROVED",
                "category", "UTILITY",
                "components", List.of(Map.of("type", "BODY", "text", "Ola {{vr_nome}"))
        );

        assertFalse(mapper.map(mixed).dto().suportado());
        assertFalse(mapper.map(incomplete).dto().suportado());
    }

    @Test
    void should_mark_named_dynamic_url_as_unsupported() {
        Map<String, Object> raw = Map.of(
                "id", "tpl-named-url",
                "name", "url_nomeada",
                "language", "pt_BR",
                "status", "APPROVED",
                "category", "UTILITY",
                "parameter_format", "NAMED",
                "components", List.of(
                        Map.of("type", "BODY", "text", "Confira"),
                        Map.of("type", "BUTTONS", "buttons", List.of(
                                Map.of("type", "URL", "text", "Abrir", "url", "https://example.test/{{vr_id}}")
                        ))
                )
        );

        var result = mapper.map(raw).dto();

        assertFalse(result.suportado());
        assertTrue(result.motivoNaoSuportado().contains("URL dinamica"));
    }

    @Test
    void should_show_pending_template_but_block_sending() {
        for (String status : List.of("PENDING", "PAUSED", "REJECTED")) {
            var definition = mapper.map(supportedTemplate(status));

            assertEquals(status, definition.dto().status());
            assertThrows(BadRequestException.class, () -> mapper.prepare(definition, List.of()));
        }
    }

    @Test
    void should_mark_media_header_as_unsupported() {
        Map<String, Object> raw = Map.of(
                "id", "tpl-media",
                "name", "imagem",
                "language", "pt_BR",
                "status", "APPROVED",
                "category", "UTILITY",
                "components", List.of(
                        Map.of("type", "HEADER", "format", "IMAGE"),
                        Map.of("type", "BODY", "text", "Confira")
                )
        );

        var result = mapper.map(raw).dto();

        assertFalse(result.suportado());
        assertTrue(result.motivoNaoSuportado().contains("midia"));
    }

    @Test
    void should_mark_flow_button_as_unsupported() {
        Map<String, Object> raw = Map.of(
                "id", "tpl-flow",
                "name", "fluxo",
                "language", "pt_BR",
                "status", "APPROVED",
                "category", "UTILITY",
                "components", List.of(Map.of(
                        "type", "BUTTONS",
                        "buttons", List.of(Map.of("type", "FLOW", "text", "Abrir"))
                ))
        );

        assertFalse(mapper.map(raw).dto().suportado());
    }

    @Test
    void should_reject_missing_extra_empty_and_duplicate_parameters() {
        var definition = mapper.map(supportedTemplate("APPROVED"));
        var valid = List.of(
                parameter("HEADER", 1, null, "Paciente"),
                parameter("BODY", 1, null, "16/07/2026"),
                parameter("BUTTON", 1, 0, "acao")
        );

        assertThrows(BadRequestException.class, () -> mapper.prepare(definition, valid.subList(0, 2)));
        assertThrows(BadRequestException.class, () -> mapper.prepare(definition,
                java.util.stream.Stream.concat(valid.stream(),
                        java.util.stream.Stream.of(parameter("BODY", 2, null, "extra"))).toList()));
        assertThrows(BadRequestException.class, () -> mapper.prepare(definition, List.of(
                parameter("HEADER", 1, null, " "), valid.get(1), valid.get(2)
        )));
        assertThrows(BadRequestException.class, () -> mapper.prepare(definition, List.of(
                valid.get(0), valid.get(0), valid.get(1), valid.get(2)
        )));
        assertThrows(BadRequestException.class, () -> mapper.prepare(definition, List.of(
                parameter("HEADER", 1, null, "Paciente\u0000"), valid.get(1), valid.get(2)
        )));
    }

    private Map<String, Object> supportedTemplate(String status) {
        return Map.of(
                "id", "tpl-1",
                "name", "confirmacao_consulta",
                "language", "pt_BR",
                "status", status,
                "category", "UTILITY",
                "components", List.of(
                        Map.of("type", "HEADER", "format", "TEXT", "text", "Ola {{1}}"),
                        Map.of("type", "BODY", "text", "Sua consulta sera em {{1}}."),
                        Map.of("type", "FOOTER", "text", "Clinica"),
                        Map.of("type", "BUTTONS", "buttons", List.of(
                                Map.of("type", "URL", "text", "Confirmar", "url", "https://example.test/{{1}}"),
                                Map.of("type", "QUICK_REPLY", "text", "Cancelar")
                        ))
                )
        );
    }

    private Map<String, Object> namedTemplate() {
        return Map.of(
                "id", "tpl-named",
                "name", "confirmacao_nomeada",
                "language", "pt_BR",
                "status", "APPROVED",
                "category", "UTILITY",
                "parameter_format", "NAMED",
                "components", List.of(
                        Map.of("type", "HEADER", "format", "TEXT", "text", "{{vr_titulo}}"),
                        Map.of("type", "BODY", "text", "Ola {{vr_nome}}")
                )
        );
    }

    private EnviarTemplateWhatsappRequest.Parametro parameter(
            String component,
            int position,
            Integer buttonIndex,
            String value
    ) {
        return new EnviarTemplateWhatsappRequest.Parametro(component, position, buttonIndex, null, value);
    }

    private EnviarTemplateWhatsappRequest.Parametro namedParameter(
            String component,
            int position,
            Integer buttonIndex,
            String parameterName,
            String value
    ) {
        return new EnviarTemplateWhatsappRequest.Parametro(
                component, position, buttonIndex, parameterName, value
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parameters(Map<String, Object> component) {
        return (List<Map<String, Object>>) component.get("parameters");
    }
}
