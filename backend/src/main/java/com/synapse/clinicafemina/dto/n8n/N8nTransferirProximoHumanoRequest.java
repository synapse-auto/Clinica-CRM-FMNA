package com.synapse.clinicafemina.dto.n8n;

import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.exception.N8nTransferPayloadException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Contrato do rodízio N8N: a ordem da lista define a ordem de atendimento. */
public record N8nTransferirProximoHumanoRequest(
        List<Long> atendentesIds,
        String motivo,
        String motivoTransferencia,
        String resumoTransferencia
) {

    private static final int MAX_ATENDENTES = 20;
    private static final int MOTIVO_MAX_LENGTH = 500;
    private static final int RESUMO_MAX_LENGTH = 4096;

    public TransferirAtendimentoRequest paraTransferencia(Long novoAtendenteId) {
        validar();
        return new TransferirAtendimentoRequest(
                novoAtendenteId,
                null,
                normalizarTexto(resumoTransferencia),
                normalizarTexto(motivoEfetivo())
        );
    }

    public List<Long> idsOrdenados() {
        validar();
        return List.copyOf(atendentesIds);
    }

    public String validarIdempotencyKey(String idempotencyKey) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            errors.put("idempotencyKey", "Informe o header Idempotency-Key.");
        } else if (idempotencyKey.length() > 120) {
            errors.put("idempotencyKey", "Informe no máximo 120 caracteres.");
        }
        if (!errors.isEmpty()) {
            throw new N8nTransferPayloadException(errors);
        }
        return idempotencyKey.trim();
    }

    private void validar() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (atendentesIds == null || atendentesIds.isEmpty()) {
            errors.put("atendentesIds", "Informe ao menos um atendente para o rodízio.");
        } else if (atendentesIds.size() > MAX_ATENDENTES) {
            errors.put("atendentesIds", "Informe no máximo " + MAX_ATENDENTES + " atendentes.");
        } else if (atendentesIds.stream().anyMatch(id -> id == null || id <= 0)) {
            errors.put("atendentesIds", "Os IDs de atendente devem ser números positivos.");
        } else if (new LinkedHashSet<>(atendentesIds).size() != atendentesIds.size()) {
            errors.put("atendentesIds", "Não repita atendentes no rodízio.");
        }
        validarTexto("motivoTransferencia", motivoEfetivo(), MOTIVO_MAX_LENGTH, errors);
        validarTexto("resumoTransferencia", resumoTransferencia, RESUMO_MAX_LENGTH, errors);
        if (!errors.isEmpty()) {
            throw new N8nTransferPayloadException(errors);
        }
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

    private String motivoEfetivo() {
        return motivoTransferencia != null ? motivoTransferencia : motivo;
    }
}
