package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.dto.EnviarTemplateWhatsappRequest;
import com.synapse.clinicafemina.dto.WhatsappTemplateDTO;
import com.synapse.clinicafemina.exception.WhatsappTemplateParametersException;
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
import java.util.stream.Collectors;

@Component
public class WhatsappTemplateParameterMapper {

    private static final int MAX_PARAMETER_NAME_LENGTH = 64;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^{}]+)}}");
    private static final Pattern NUMERIC_NAME = Pattern.compile("[1-9]\\d{0,2}");
    private static final Pattern NAMED_NAME = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]{0," + (MAX_PARAMETER_NAME_LENGTH - 1) + "}"
    );
    private static final Pattern INVALID_CONTROL = Pattern.compile("[\\p{Cc}&&[^\\r\\n\\t]]");

    public FormatResolution resolveFormat(Object declaredValue, List<Map<String, Object>> components) {
        PlaceholderScan scan = scanComponents(components);
        String declared = Objects.toString(declaredValue, "").trim().toUpperCase(Locale.ROOT);
        if (!declared.isBlank() && !Set.of("POSITIONAL", "NAMED").contains(declared)) {
            return FormatResolution.unsupported("Formato de parametros do template nao suportado.");
        }
        if (scan.invalid()) {
            return FormatResolution.unsupported("O template possui uma variavel com formato invalido.");
        }
        if (scan.numeric() && scan.named()) {
            return FormatResolution.unsupported("O template mistura variaveis posicionais e nomeadas.");
        }
        if (("POSITIONAL".equals(declared) && scan.named())
                || ("NAMED".equals(declared) && scan.numeric())) {
            return FormatResolution.unsupported("As variaveis divergem do formato oficial do template.");
        }
        if ("NAMED".equals(declared) || declared.isBlank() && scan.named()) {
            return new FormatResolution(ParameterFormat.NAMED, null);
        }
        if ("POSITIONAL".equals(declared) || scan.numeric()) {
            return new FormatResolution(ParameterFormat.POSITIONAL, null);
        }
        return new FormatResolution(ParameterFormat.NONE, null);
    }

    public List<WhatsappTemplateDTO.VariableDTO> extractVariables(
            String text,
            String component,
            Integer buttonIndex,
            ParameterFormat format
    ) {
        if (text == null || text.isBlank() || format == ParameterFormat.NONE) {
            return List.of();
        }
        Map<String, WhatsappTemplateDTO.VariableDTO> variables = new LinkedHashMap<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (format == ParameterFormat.POSITIONAL && NUMERIC_NAME.matcher(token).matches()) {
                int position = Integer.parseInt(token);
                variables.putIfAbsent(token,
                        new WhatsappTemplateDTO.VariableDTO(component, position, buttonIndex, null));
            } else if (format == ParameterFormat.NAMED && NAMED_NAME.matcher(token).matches()) {
                int visualPosition = variables.size() + 1;
                variables.putIfAbsent(token,
                        new WhatsappTemplateDTO.VariableDTO(component, visualPosition, buttonIndex, token));
            }
        }
        return List.copyOf(variables.values());
    }

    public Map<ParameterKey, String> validateParameters(
            List<WhatsappTemplateDTO.VariableDTO> expected,
            List<EnviarTemplateWhatsappRequest.Parametro> received
    ) {
        Map<ParameterKey, String> values = new HashMap<>();
        for (EnviarTemplateWhatsappRequest.Parametro parameter : received) {
            ParameterKey key = validateParameter(parameter);
            if (values.putIfAbsent(key, parameter.valor()) != null) {
                throw invalid("Parametro de template duplicado");
            }
        }
        Set<ParameterKey> expectedKeys = expected.stream()
                .map(this::key)
                .collect(Collectors.toSet());
        if (!values.keySet().equals(expectedKeys)) {
            throw invalid("Os parametros informados nao correspondem ao template oficial");
        }
        return values;
    }

    public List<Map<String, Object>> buildMetaComponents(
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
                    .map(variable -> metaParameter(variable, values.get(key(variable))))
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

    public String replaceVariables(
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
                String placeholder = key.parameterName() == null
                        ? String.valueOf(key.position())
                        : key.parameterName();
                rendered = rendered.replace("{{" + placeholder + "}}", entry.getValue());
            }
        }
        return rendered;
    }

    private ParameterKey validateParameter(EnviarTemplateWhatsappRequest.Parametro parameter) {
        if (parameter == null) {
            throw invalid("Parametro de template incompleto");
        }
        if (parameter.componente() == null || parameter.posicao() == null) {
            throw invalid("Parametro de template incompleto");
        }
        String value = parameter.valor();
        if (value == null || value.isBlank()) {
            throw invalid("Valores de template nao podem ser vazios");
        }
        if (INVALID_CONTROL.matcher(value).find()) {
            throw invalid("Valor de template contem caracteres invalidos");
        }
        String parameterName = parameter.nomeParametro();
        if (parameterName != null && !NAMED_NAME.matcher(parameterName).matches()) {
            throw invalid("Nome de parametro de template invalido");
        }
        return new ParameterKey(
                parameter.componente().toUpperCase(Locale.ROOT),
                parameter.posicao(),
                parameter.indiceBotao(),
                parameterName
        );
    }

    private Map<String, Object> metaParameter(WhatsappTemplateDTO.VariableDTO variable, String value) {
        Map<String, Object> parameter = new LinkedHashMap<>();
        parameter.put("type", "text");
        if (variable.nomeParametro() != null) {
            parameter.put("parameter_name", variable.nomeParametro());
        }
        parameter.put("text", value);
        return parameter;
    }

    private ParameterKey key(WhatsappTemplateDTO.VariableDTO variable) {
        return new ParameterKey(
                variable.componente(),
                variable.posicao(),
                variable.indiceBotao(),
                variable.nomeParametro()
        );
    }

    private PlaceholderScan scanComponents(List<Map<String, Object>> components) {
        PlaceholderScan result = PlaceholderScan.empty();
        for (Map<String, Object> component : components) {
            result = result.merge(scanText(Objects.toString(component.get("text"), "")));
            for (Map<String, Object> button : maps(component.get("buttons"))) {
                result = result.merge(scanText(Objects.toString(button.get("url"), "")));
            }
        }
        return result;
    }

    private PlaceholderScan scanText(String text) {
        boolean numeric = false;
        boolean named = false;
        boolean invalid = false;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group(1);
            numeric |= NUMERIC_NAME.matcher(token).matches();
            named |= NAMED_NAME.matcher(token).matches();
            invalid |= !NUMERIC_NAME.matcher(token).matches() && !NAMED_NAME.matcher(token).matches();
        }
        String withoutValidPlaceholders = matcher.replaceAll("");
        invalid |= withoutValidPlaceholders.contains("{{") || withoutValidPlaceholders.contains("}}");
        return new PlaceholderScan(numeric, named, invalid);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item).toList();
    }

    private WhatsappTemplateParametersException invalid(String message) {
        return new WhatsappTemplateParametersException(message);
    }

    public enum ParameterFormat { NONE, POSITIONAL, NAMED, UNSUPPORTED }

    public record FormatResolution(ParameterFormat format, String unsupportedReason) {
        static FormatResolution unsupported(String reason) {
            return new FormatResolution(ParameterFormat.UNSUPPORTED, reason);
        }
    }

    public record ParameterKey(
            String component,
            int position,
            Integer buttonIndex,
            String parameterName
    ) {
    }

    private record ComponentKey(String component, Integer buttonIndex) {
    }

    private record PlaceholderScan(boolean numeric, boolean named, boolean invalid) {
        static PlaceholderScan empty() {
            return new PlaceholderScan(false, false, false);
        }

        PlaceholderScan merge(PlaceholderScan other) {
            return new PlaceholderScan(
                    numeric || other.numeric,
                    named || other.named,
                    invalid || other.invalid
            );
        }
    }
}
