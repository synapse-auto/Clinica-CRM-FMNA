package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.dto.EnviarTemplateWhatsappRequest;
import com.synapse.clinicafemina.dto.WhatsappTemplateDTO;
import com.synapse.clinicafemina.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WhatsappTemplateMapper {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\d+)}}", Pattern.CASE_INSENSITIVE);
    private static final Pattern INVALID_CONTROL = Pattern.compile("[\\p{Cc}&&[^\\r\\n\\t]]");
    private static final Set<String> SUPPORTED_BUTTONS = Set.of("QUICK_REPLY", "PHONE_NUMBER", "URL");

    public TemplateDefinition map(Map<String, Object> raw) {
        String id = text(raw.get("id"));
        String name = text(raw.get("name"));
        String language = text(raw.get("language"));
        String status = upper(raw.get("status"));
        String category = upper(raw.get("category"));
        List<Map<String, Object>> components = maps(raw.get("components"));

        TemplateParts parts = parseParts(components, category);
        WhatsappTemplateDTO dto = new WhatsappTemplateDTO(
                id,
                name,
                language,
                status,
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
        Map<ParameterKey, String> values = validateParameters(definition.dto().variaveis(), parameters);
        List<Map<String, Object>> metaComponents = buildMetaComponents(definition.dto().variaveis(), values);
        String preview = renderPreview(definition.dto(), values);
        return new PreparedTemplate(preview, metaComponents);
    }

    private TemplateParts parseParts(List<Map<String, Object>> components, String category) {
        String header = null;
        String body = null;
        String footer = null;
        String unsupported = "AUTHENTICATION".equals(category)
                ? "Templates de autenticacao ainda nao sao suportados."
                : null;
        List<WhatsappTemplateDTO.ButtonDTO> buttons = new ArrayList<>();
        List<WhatsappTemplateDTO.VariableDTO> variables = new ArrayList<>();

        for (Map<String, Object> component : components) {
            String type = upper(component.get("type"));
            switch (type) {
                case "HEADER" -> {
                    String format = upper(component.get("format"));
                    if (!format.isBlank() && !"TEXT".equals(format)) {
                        unsupported = "Templates com cabecalho de midia ainda nao sao suportados.";
                    }
                    header = text(component.get("text"));
                    variables.addAll(extractVariables(header, "HEADER", null));
                }
                case "BODY" -> {
                    body = text(component.get("text"));
                    variables.addAll(extractVariables(body, "BODY", null));
                }
                case "FOOTER" -> {
                    footer = text(component.get("text"));
                    if (!extractVariables(footer, "FOOTER", null).isEmpty()) {
                        unsupported = "Templates com variaveis no rodape ainda nao sao suportados.";
                    }
                }
                case "BUTTONS" -> {
                    ButtonParts result = parseButtons(component);
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

    private ButtonParts parseButtons(Map<String, Object> component) {
        List<WhatsappTemplateDTO.ButtonDTO> buttons = new ArrayList<>();
        List<WhatsappTemplateDTO.VariableDTO> variables = new ArrayList<>();
        String unsupported = null;
        List<Map<String, Object>> rawButtons = maps(component.get("buttons"));
        for (int index = 0; index < rawButtons.size(); index++) {
            Map<String, Object> rawButton = rawButtons.get(index);
            String type = upper(rawButton.get("type"));
            String buttonText = text(rawButton.get("text"));
            String url = text(rawButton.get("url"));
            buttons.add(new WhatsappTemplateDTO.ButtonDTO(type, buttonText, url.isBlank() ? null : url));
            if (!SUPPORTED_BUTTONS.contains(type)) {
                unsupported = "Este tipo de botao ainda nao e suportado.";
            }
            if ("URL".equals(type)) {
                variables.addAll(extractVariables(url, "BUTTON", index));
            }
        }
        return new ButtonParts(buttons, variables, unsupported);
    }

    private List<WhatsappTemplateDTO.VariableDTO> extractVariables(
            String text,
            String component,
            Integer buttonIndex
    ) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<WhatsappTemplateDTO.VariableDTO> variables = new ArrayList<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        while (matcher.find()) {
            int position = Integer.parseInt(matcher.group(1));
            var variable = new WhatsappTemplateDTO.VariableDTO(component, position, buttonIndex);
            if (!variables.contains(variable)) {
                variables.add(variable);
            }
        }
        return variables;
    }

    private Map<ParameterKey, String> validateParameters(
            List<WhatsappTemplateDTO.VariableDTO> expected,
            List<EnviarTemplateWhatsappRequest.Parametro> received
    ) {
        Map<ParameterKey, String> values = new HashMap<>();
        for (EnviarTemplateWhatsappRequest.Parametro parameter : received) {
            if (parameter.componente() == null || parameter.posicao() == null) {
                throw new BadRequestException("Parametro de template incompleto");
            }
            String value = parameter.valor();
            if (value == null || value.isBlank()) {
                throw new BadRequestException("Valores de template nao podem ser vazios");
            }
            if (INVALID_CONTROL.matcher(value).find()) {
                throw new BadRequestException("Valor de template contem caracteres invalidos");
            }
            ParameterKey key = new ParameterKey(
                    upper(parameter.componente()), parameter.posicao(), parameter.indiceBotao()
            );
            if (values.putIfAbsent(key, value) != null) {
                throw new BadRequestException("Parametro de template duplicado");
            }
        }
        Set<ParameterKey> expectedKeys = expected.stream()
                .map(item -> new ParameterKey(item.componente(), item.posicao(), item.indiceBotao()))
                .collect(java.util.stream.Collectors.toSet());
        if (!values.keySet().equals(expectedKeys)) {
            throw new BadRequestException("Os parametros informados nao correspondem ao template oficial");
        }
        return values;
    }

    private List<Map<String, Object>> buildMetaComponents(
            List<WhatsappTemplateDTO.VariableDTO> variables,
            Map<ParameterKey, String> values
    ) {
        Map<ComponentKey, List<WhatsappTemplateDTO.VariableDTO>> grouped = new LinkedHashMap<>();
        for (WhatsappTemplateDTO.VariableDTO variable : variables) {
            ComponentKey key = new ComponentKey(variable.componente(), variable.indiceBotao());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(variable);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<ComponentKey, List<WhatsappTemplateDTO.VariableDTO>> entry : grouped.entrySet()) {
            entry.getValue().sort(Comparator.comparingInt(WhatsappTemplateDTO.VariableDTO::posicao));
            List<Map<String, Object>> parameters = entry.getValue().stream()
                    .map(variable -> Map.<String, Object>of(
                            "type", "text",
                            "text", values.get(new ParameterKey(
                                    variable.componente(), variable.posicao(), variable.indiceBotao()
                            ))
                    ))
                    .toList();
            Map<String, Object> component = new LinkedHashMap<>();
            component.put("type", entry.getKey().component().toLowerCase(Locale.ROOT));
            if ("BUTTON".equals(entry.getKey().component())) {
                component.put("sub_type", "url");
                component.put("index", String.valueOf(entry.getKey().buttonIndex()));
            }
            component.put("parameters", parameters);
            result.add(component);
        }
        return result;
    }

    private String renderPreview(WhatsappTemplateDTO template, Map<ParameterKey, String> values) {
        List<String> sections = new ArrayList<>();
        addSection(sections, replaceVariables(template.cabecalho(), "HEADER", null, values));
        addSection(sections, replaceVariables(template.corpo(), "BODY", null, values));
        addSection(sections, template.rodape());
        template.botoes().stream()
                .map(WhatsappTemplateDTO.ButtonDTO::texto)
                .filter(text -> text != null && !text.isBlank())
                .forEach(text -> sections.add("[Botao: " + text + "]"));
        return String.join("\n", sections);
    }

    private String replaceVariables(
            String text,
            String component,
            Integer buttonIndex,
            Map<ParameterKey, String> values
    ) {
        if (text == null) {
            return null;
        }
        String rendered = text;
        for (Map.Entry<ParameterKey, String> entry : values.entrySet()) {
            ParameterKey key = entry.getKey();
            if (key.component().equals(component) && Objects.equals(key.buttonIndex(), buttonIndex)) {
                rendered = rendered.replace("{{" + key.position() + "}}", entry.getValue());
            }
        }
        return rendered;
    }

    private void addSection(List<String> sections, String text) {
        if (text != null && !text.isBlank()) {
            sections.add(text);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
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

    private record ParameterKey(String component, int position, Integer buttonIndex) {
    }

    private record ComponentKey(String component, Integer buttonIndex) {
    }
}
