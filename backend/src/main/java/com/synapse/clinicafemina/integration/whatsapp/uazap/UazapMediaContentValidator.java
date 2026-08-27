package com.synapse.clinicafemina.integration.whatsapp.uazap;

import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Valida o Content-Type e identifica formatos binários comuns aceitos pelo WhatsApp. */
final class UazapMediaContentValidator {

    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> DOCUMENT_TYPES = Set.of(
            "application/pdf",
            "application/octet-stream",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            "application/rtf",
            "application/zip");

    private UazapMediaContentValidator() {}

    static void validarHeader(String mimeType) {
        if (mimeType != null && !permitido(mimeType)) {
            throw new UazapWhatsappMediaDownloader.MediaDownloadException("MIME_TYPE_NAO_PERMITIDO");
        }
    }

    static String resolver(String responseMimeType, String metadataMimeType, byte[] bytes) {
        String detected = detectar(bytes);
        for (String candidate : List.of(
                responseMimeType == null ? "" : responseMimeType,
                metadataMimeType == null ? "" : metadataMimeType,
                detected == null ? "" : detected)) {
            String normalized = normalizar(candidate);
            if (permitido(normalized) && !"application/octet-stream".equals(normalized)) {
                return normalized;
            }
        }
        if ("application/octet-stream".equals(normalizar(responseMimeType))) {
            return "application/octet-stream";
        }
        throw new UazapWhatsappMediaDownloader.MediaDownloadException("MIME_TYPE_AUSENTE_OU_INVALIDO");
    }

    private static boolean permitido(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) return false;
        if (IMAGE_TYPES.contains(mimeType) || mimeType.startsWith("audio/") || mimeType.startsWith("video/")) {
            return true;
        }
        return DOCUMENT_TYPES.contains(mimeType)
                || mimeType.equals("text/plain")
                || mimeType.equals("text/csv")
                || mimeType.equals("text/rtf");
    }

    private static String detectar(byte[] bytes) {
        if (startsWith(bytes, 0xff, 0xd8, 0xff)) return "image/jpeg";
        if (startsWith(bytes, 0x89, 0x50, 0x4e, 0x47)) return "image/png";
        if (asciiAt(bytes, 0, "GIF8")) return "image/gif";
        if (asciiAt(bytes, 0, "%PDF")) return "application/pdf";
        if (asciiAt(bytes, 0, "OggS")) return "audio/ogg";
        if (asciiAt(bytes, 0, "ID3")) return "audio/mpeg";
        if (asciiAt(bytes, 4, "ftyp")) return "video/mp4";
        if (asciiAt(bytes, 0, "RIFF") && asciiAt(bytes, 8, "WEBP")) return "image/webp";
        if (asciiAt(bytes, 0, "RIFF") && asciiAt(bytes, 8, "WAVE")) return "audio/wav";
        if (startsWith(bytes, 0x50, 0x4b, 0x03, 0x04)) return "application/zip";
        return null;
    }

    private static String normalizar(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) return null;
        try {
            MediaType parsed = MediaType.parseMediaType(mimeType);
            return (parsed.getType() + "/" + parsed.getSubtype()).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xff) != signature[i]) return false;
        }
        return true;
    }

    private static boolean asciiAt(byte[] bytes, int offset, String signature) {
        byte[] expected = signature.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if (bytes[offset + i] != expected[i]) return false;
        }
        return true;
    }
}
