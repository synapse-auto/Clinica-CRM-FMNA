package com.synapse.clinicafemina.dto.n8n;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.exception.N8nTransferPayloadException;
import java.util.LinkedHashMap;
import java.util.Map;

public record N8nTransferirHumanoRequest(
        @JsonAlias({"atendenteId", "novo_atendente_id", "atendente_id"}) JsonNode novoAtendenteId,
        String motivo,
        String motivoTransferencia,
        String resumoTransferencia
) {

    private static final String ID_ERROR = "Informe um ID numérico de gestor ou recepcionista.";
    private static final int MOTIVO_MAX_LENGTH = 500;
    private static final int RESUMO_MAX_LENGTH = 4096;

    public TransferirAtendimentoRequest paraTransferencia() {
        Map<String, String> errors = new LinkedHashMap<>();
        Long atendenteId = normalizarAtendenteId(errors);
        validarTexto("motivoTransferencia", motivoEfetivo(), MOTIVO_MAX_LENGTH, errors);
        validarTexto("resumoTransferencia", resumoTransferencia, RESUMO_MAX_LENGTH, errors);
        if (!errors.isEmpty()) {
            throw new N8nTransferPayloadException(errors);
        }

        return new TransferirAtendimentoRequest(
                atendenteId,
                null,
                normalizarTexto(resumoTransferencia),
                normalizarTexto(motivoEfetivo())
        );
    }

    public String tipoNovoAtendenteId() {
        if (novoAtendenteId == null || novoAtendenteId.isNull()) {
            return "ausente";
        }
        return novoAtendenteId.getNodeType().name().toLowerCase();
    }

    public int motivoChars() {
        return tamanho(motivoEfetivo());
    }

    public int resumoChars() {
        return tamanho(resumoTransferencia);
    }

    private Long normalizarAtendenteId(Map<String, String> errors) {
        if (novoAtendenteId == null || novoAtendenteId.isNull()) {
            errors.put("novoAtendenteId", ID_ERROR);
            return null;
        }

        if (novoAtendenteId.isIntegralNumber() && novoAtendenteId.canConvertToLong()) {
            long value = novoAtendenteId.longValue();
            if (value > 0) {
                return value;
            }
        } else if (novoAtendenteId.isTextual()) {
            String value = novoAtendenteId.textValue() == null ? "" : novoAtendenteId.textValue().trim();
            if (value.matches("[1-9]\\d*")) {
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException ignored) {
                    // IDs fora do intervalo de Long usam a mesma mensagem do contrato.
                }
            }
        }

        errors.put("novoAtendenteId", ID_ERROR);
        return null;
    }

    private void validarTexto(String field, String value, int maxLength, Map<String, String> errors) {
        if (value != null && value.length() > maxLength) {
            errors.put(field, "Informe no máximo " + maxLength + " caracteres.");
        }
    }

    private String normalizarTexto(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("<[^>]*>", "").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int tamanho(String value) {
        return value == null ? 0 : value.length();
    }

    private String motivoEfetivo() {
        return motivoTransferencia != null ? motivoTransferencia : motivo;
    }
}
