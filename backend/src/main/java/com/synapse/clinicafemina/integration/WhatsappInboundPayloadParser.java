package com.synapse.clinicafemina.integration;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WhatsappInboundPayloadParser {

    @SuppressWarnings("unchecked")
    public DadosMensagem extrairDados(Map<String, Object> mensagem) {
        String tipo = String.valueOf(mensagem.getOrDefault("type", "text"));
        if ("text".equals(tipo)) {
            Map<String, Object> texto = (Map<String, Object>) mensagem.get("text");
            return new DadosMensagem(
                    "TEXTO",
                    texto == null ? "" : String.valueOf(texto.getOrDefault("body", "")),
                    null,
                    "text/plain",
                    null
            );
        }

        Map<String, Object> media = (Map<String, Object>) mensagem.get(tipo);
        if (media == null || media.get("id") == null) {
            throw new IllegalArgumentException("Payload de mídia incompleto");
        }
        String tipoMedia = mapearTipoMedia(tipo);
        String nome = String.valueOf(media.getOrDefault("filename", tipoMedia.toLowerCase()));
        String legenda = String.valueOf(media.getOrDefault("caption", ""));
        String conteudo = legenda.isBlank() ? "[" + tipoMedia + "] " + nome : legenda;
        return new DadosMensagem(
                tipoMedia,
                conteudo,
                String.valueOf(media.get("id")),
                String.valueOf(media.getOrDefault("mime_type", mimePadrao(tipoMedia))),
                nome
        );
    }

    public String normalizarTelefone(String telefone) {
        return telefone.startsWith("+") ? telefone.substring(1) : telefone;
    }

    public String limitarPrevia(String conteudo) {
        return conteudo.length() > 60 ? conteudo.substring(0, 60) + "…" : conteudo;
    }

    private String mapearTipoMedia(String tipo) {
        return switch (tipo) {
            case "image" -> "IMAGEM";
            case "audio" -> "AUDIO";
            case "document" -> "DOCUMENTO";
            default -> throw new IllegalArgumentException("Tipo de mensagem não suportado: " + tipo);
        };
    }

    private String mimePadrao(String tipoMedia) {
        return switch (tipoMedia) {
            case "IMAGEM" -> "image/jpeg";
            case "AUDIO" -> "audio/ogg";
            default -> "application/octet-stream";
        };
    }

    public record DadosMensagem(
            String tipoMedia,
            String conteudo,
            String mediaId,
            String mimeType,
            String nomeArquivo
    ) {
    }
}
