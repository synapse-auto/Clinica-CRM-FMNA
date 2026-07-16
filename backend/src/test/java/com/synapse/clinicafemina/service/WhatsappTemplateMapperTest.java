package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.dto.EnviarTemplateWhatsappRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsappTemplateMapperTest {

    private final WhatsappTemplateMapper mapper = new WhatsappTemplateMapper();

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

    private EnviarTemplateWhatsappRequest.Parametro parameter(
            String component,
            int position,
            Integer buttonIndex,
            String value
    ) {
        return new EnviarTemplateWhatsappRequest.Parametro(component, position, buttonIndex, value);
    }
}
