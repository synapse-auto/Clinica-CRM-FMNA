package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.dto.EnviarTemplateWhatsappRequest;
import com.synapse.clinicafemina.dto.WhatsappTemplateDTO;
import com.synapse.clinicafemina.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class WhatsappTemplateMapper {

    private static final Set<String> SUPPORTED_BUTTONS = Set.of("QUICK_REPLY", "PHONE_NUMBER", "URL");

    private final WhatsappTemplateParameterMapper parameterMapper;

    public WhatsappTemplateMapper(WhatsappTemplateParameterMapper parameterMapper) {
        this.parameterMapper = parameterMapper;
    }

    public TemplateDefinition map(Map<String, Object> raw) {
        String category = upper(raw.get("category"));
        List<Map<String, Object>> components = maps(raw.get("components"));
        var format = parameterMapper.resolveFormat(raw.get("parameter_format"), components);
        TemplateParts parts = parseParts(components, category, format);
        WhatsappTemplateDTO dto = new WhatsappTemplateDTO(
                text(raw.get("id")),
                text(raw.get("name")),
                text(raw.get("language")),
                upper(raw.get("status")),
                category,
                parts.header(),
                parts.body(),
                parts.footer(),
                parts.buttons(),
                parts.variables(),
                parts.unsupportedReason() == null,
                parts.unsupportedReason()
        );
        return new TemplateDefinition(dto, components);
    }

    public PreparedTemplate prepare(
            TemplateDefinition definition,
            List<EnviarTemplateWhatsappRequest.Parametro> parameters
    ) {
        if (!"APPROVED".equals(definition.dto().status())) {
            throw new BadRequestException("Somente templates aprovados podem ser enviados");
        }
        if (!definition.dto().suportado()) {
            throw new BadRequestException(definition.dto().motivoNaoSuportado());
        }
        var values = parameterMapper.validateParameters(definition.dto().variaveis(), parameters);
        List<Map<String, Object>> metaComponents = parameterMapper.buildMetaComponents(
                definition.dto().variaveis(), values
        );
        return new PreparedTemplate(renderPreview(definition.dto(), values), metaComponents);
    }

    private TemplateParts parseParts(
            List<Map<String, Object>> components,
            String category,
            WhatsappTemplateParameterMapper.FormatResolution format
    ) {
        String header = null;
        String body = null;
        String footer = null;
        String unsupported = "AUTHENTICATION".equals(category)
                ? "Templates de autenticacao ainda nao sao suportados."
                : format.unsupportedReason();
        List<WhatsappTemplateDTO.ButtonDTO> buttons = new ArrayList<>();
        List<WhatsappTemplateDTO.VariableDTO> variables = new ArrayList<>();

        for (Map<String, Object> component : components) {
            String type = upper(component.get("type"));
            switch (type) {
                case "HEADER" -> {
                    String headerFormat = upper(component.get("format"));
                    if (!headerFormat.isBlank() && !"TEXT".equals(headerFormat)) {
                        unsupported = "Templates com cabecalho de midia ainda nao sao suportados.";
                    }
                    header = text(component.get("text"));
                    variables.addAll(extract(header, "HEADER", null, format));
                }
                case "BODY" -> {
                    body = text(component.get("text"));
                    variables.addAll(extract(body, "BODY", null, format));
                }
                case "FOOTER" -> {
                    footer = text(component.get("text"));
                    if (!extract(footer, "FOOTER", null, format).isEmpty()) {
                        unsupported = "Templates com variaveis no rodape ainda nao sao suportados.";
                    }
                }
                case "BUTTONS" -> {
                    ButtonParts result = parseButtons(component, format);
                    buttons.addAll(result.buttons());
                    variables.addAll(result.variables());
                    if (result.unsupportedReason() != null) {
                        unsupported = result.unsupportedReason();
                    }
                }
                default -> unsupported = "Este formato de template ainda nao e suportado.";
            }
        }
        variables.sort(Comparator.comparing(WhatsappTemplateDTO.VariableDTO::componente)
                .thenComparing(variable -> Objects.requireNonNullElse(variable.indiceBotao(), -1))
                .thenComparingInt(WhatsappTemplateDTO.VariableDTO::posicao));
        return new TemplateParts(header, body, footer, buttons, variables, unsupported);
    }

    private ButtonParts parseButtons(
            Map<String, Object> component,
            WhatsappTemplateParameterMapper.FormatResolution format
    ) {
        List<WhatsappTemplateDTO.ButtonDTO> buttons = new ArrayList<>();
        List<WhatsappTemplateDTO.VariableDTO> variables = new ArrayList<>();
        String unsupported = null;
        List<Map<String, Object>> rawButtons = maps(component.get("buttons"));
        for (int index = 0; index < rawButtons.size(); index++) {
            Map<String, Object> rawButton = rawButtons.get(index);
            String type = upper(rawButton.get("type"));
            String url = text(rawButton.get("url"));
            buttons.add(new WhatsappTemplateDTO.ButtonDTO(
                    type,
                    text(rawButton.get("text")),
                    url.isBlank() ? null : url
            ));
            if (!SUPPORTED_BUTTONS.contains(type)) {
                unsupported = "Este tipo de botao ainda nao e suportado.";
            }
            if ("URL".equals(type)) {
                List<WhatsappTemplateDTO.VariableDTO> urlVariables = extract(url, "BUTTON", index, format);
                variables.addAll(urlVariables);
                if (format.format() == WhatsappTemplateParameterMapper.ParameterFormat.NAMED
                        && !urlVariables.isEmpty()) {
                    unsupported = "URL dinamica com variavel nomeada ainda nao e suportada.";
                }
            }
        }
        return new ButtonParts(buttons, variables, unsupported);
    }

    private List<WhatsappTemplateDTO.VariableDTO> extract(
            String text,
            String component,
            Integer buttonIndex,
            WhatsappTemplateParameterMapper.FormatResolution format
    ) {
        return parameterMapper.extractVariables(text, component, buttonIndex, format.format());
    }

    private String renderPreview(
            WhatsappTemplateDTO template,
            Map<WhatsappTemplateParameterMapper.ParameterKey, String> values
    ) {
        List<String> sections = new ArrayList<>();
        addSection(sections, parameterMapper.replaceVariables(template.cabecalho(), "HEADER", null, values));
        addSection(sections, parameterMapper.replaceVariables(template.corpo(), "BODY", null, values));
        addSection(sections, template.rodape());
        template.botoes().stream()
                .map(WhatsappTemplateDTO.ButtonDTO::texto)
                .filter(value -> value != null && !value.isBlank())
                .forEach(value -> sections.add("[Botao: " + value + "]"));
        return String.join("\n", sections);
    }

    private void addSection(List<String> sections, String value) {
        if (value != null && !value.isBlank()) {
            sections.add(value);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item).toList();
    }

    private String text(Object value) {
        return Objects.toString(value, "").trim();
    }

    private String upper(Object value) {
        return text(value).toUpperCase(Locale.ROOT);
    }

    public record TemplateDefinition(WhatsappTemplateDTO dto, List<Map<String, Object>> components) {
    }

    public record PreparedTemplate(String preview, List<Map<String, Object>> metaComponents) {
    }

    private record TemplateParts(
            String header,
            String body,
            String footer,
            List<WhatsappTemplateDTO.ButtonDTO> buttons,
            List<WhatsappTemplateDTO.VariableDTO> variables,
            String unsupportedReason
    ) {
    }

    private record ButtonParts(
            List<WhatsappTemplateDTO.ButtonDTO> buttons,
            List<WhatsappTemplateDTO.VariableDTO> variables,
            String unsupportedReason
    ) {
    }
}
