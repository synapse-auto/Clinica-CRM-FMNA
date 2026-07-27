package com.synapse.clinicafemina.integration.whatsapp.uazap;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class UazapProfilePhotoImageValidator {

    public static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    public ValidatedImage validar(byte[] bytes, String declaredContentType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("IMAGEM_VAZIA");
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("IMAGEM_EXCEDE_LIMITE");
        }

        String detected = detectarContentType(bytes);
        if (detected == null) {
            throw new IllegalArgumentException("MAGIC_BYTES_INVALIDOS");
        }

        String declared = normalizarContentType(declaredContentType);
        if (declared != null && !declared.equals(detected)) {
            throw new IllegalArgumentException("CONTENT_TYPE_DIVERGENTE");
        }
        return new ValidatedImage(bytes, detected);
    }

    private String normalizarContentType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "image/jpeg", "image/png", "image/webp" -> normalized;
            default -> throw new IllegalArgumentException("CONTENT_TYPE_NAO_SUPORTADO");
        };
    }

    private String detectarContentType(byte[] bytes) {
        if (isJpeg(bytes)) {
            return "image/jpeg";
        }
        if (isPng(bytes)) {
            return "image/png";
        }
        if (isWebp(bytes)) {
            return "image/webp";
        }
        return null;
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && unsigned(bytes[0]) == 0xFF
                && unsigned(bytes[1]) == 0xD8
                && unsigned(bytes[2]) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        int[] signature = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (bytes.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (unsigned(bytes[index]) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    public record ValidatedImage(byte[] bytes, String contentType) {
    }
}
