package com.synapse.clinicafemina.integration;

import org.springframework.stereotype.Component;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappPhoneNormalizer;

import java.util.Locale;
import java.util.Map;

@Component
public class WhatsappInboundPayloadParser {

    /**
     * Limite da coluna {@code mensagem.conteudo_previa} ({@code VARCHAR(60)} — contado em
     * <em>code points</em> pelo PostgreSQL). A prévia completa, incluindo o sufixo, nunca pode
     * ultrapassar esse valor.
     */
    static final int PREVIA_MAX_CODE_POINTS = 60;

    /** Sufixo de truncamento. Usa três pontos ASCII (não o caractere "…") para custo previsível. */
    static final String PREVIA_SUFFIX = "...";

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

        if ("reaction".equals(tipo)) {
            Map<String, Object> reaction = (Map<String, Object>) mensagem.get("reaction");
            String emoji = reaction == null ? "" : String.valueOf(reaction.getOrDefault("emoji", ""));
            return new DadosMensagem(
                    "TEXTO",
                    emoji.isBlank() ? "Paciente reagiu a uma mensagem" : "Paciente reagiu com " + emoji,
                    null,
                    "text/plain",
                    null
            );
        }

        Map<String, Object> media = (Map<String, Object>) mensagem.get(tipo);
        if (media == null || media.get("id") == null) {
            return new DadosMensagem(
                    "OUTRO",
                    conteudoGenerico(tipo),
                    null,
                    "application/octet-stream",
                    null
            );
        }
        String tipoMedia = mapearTipoMedia(tipo);
        String mimeType = String.valueOf(media.getOrDefault("mime_type", mimePadrao(tipoMedia, tipo)));
        String nome = normalizarNomeArquivo(media.get("filename"), tipo, tipoMedia, mimeType);
        String legenda = String.valueOf(media.getOrDefault("caption", ""));
        String conteudo = legenda.isBlank() ? "[" + tipoMedia + "] " + nome : legenda;
        return new DadosMensagem(
                tipoMedia,
                conteudo,
                String.valueOf(media.get("id")),
                mimeType,
                nome
        );
    }

    public String normalizarTelefone(String telefone) {
        return WhatsappPhoneNormalizer.normalize(telefone);
    }

    /**
     * Limita a prévia da mensagem de forma <strong>Unicode-safe</strong>:
     * <ul>
     *   <li>conta por <em>code points</em> (não por unidades UTF-16), coerente com o
     *       {@code VARCHAR(60)} do PostgreSQL;</li>
     *   <li>até {@link #PREVIA_MAX_CODE_POINTS} code points, retorna o conteúdo inalterado;</li>
     *   <li>acima do limite, mantém os primeiros
     *       {@code (PREVIA_MAX_CODE_POINTS - "...".length)} code points e acrescenta o sufixo,
     *       garantindo que o resultado completo caiba no limite da coluna;</li>
     *   <li>usa {@link String#offsetByCodePoints} para nunca partir um par surrogate ao meio —
     *       cortar um emoji no meio produziria um surrogate solto, inválido em UTF-8, e faria o
     *       INSERT da mensagem falhar (perdendo a mensagem no CRM e no N8N).</li>
     * </ul>
     * O {@code conteudo} original nunca é truncado por este método — apenas a prévia.
     */
    public String limitarPrevia(String conteudo) {
        if (conteudo == null) {
            return null;
        }
        int totalCodePoints = conteudo.codePointCount(0, conteudo.length());
        if (totalCodePoints <= PREVIA_MAX_CODE_POINTS) {
            return conteudo;
        }
        int sufixoCodePoints = PREVIA_SUFFIX.codePointCount(0, PREVIA_SUFFIX.length());
        int manter = PREVIA_MAX_CODE_POINTS - sufixoCodePoints;
        int fimSeguro = conteudo.offsetByCodePoints(0, manter);
        return conteudo.substring(0, fimSeguro) + PREVIA_SUFFIX;
    }

    private String mapearTipoMedia(String tipo) {
        return switch (tipo) {
            case "image", "sticker" -> "IMAGEM";
            case "audio" -> "AUDIO";
            case "document" -> "DOCUMENTO";
            default -> "OUTRO";
        };
    }

    private String conteudoGenerico(String tipo) {
        if (tipo == null || tipo.isBlank() || "null".equals(tipo)) {
            return "[MENSAGEM]";
        }
        return "[" + tipo.toUpperCase(Locale.ROOT) + "]";
    }

    private String mimePadrao(String tipoMedia, String tipoPayload) {
        if ("sticker".equals(tipoPayload)) {
            return "image/webp";
        }
        return switch (tipoMedia) {
            case "IMAGEM" -> "image/jpeg";
            case "AUDIO" -> "audio/ogg";
            default -> "application/octet-stream";
        };
    }

    private String normalizarNomeArquivo(Object nomeRecebido, String tipoPayload, String tipoMedia, String mimeType) {
        String nome = nomeRecebido == null ? "" : String.valueOf(nomeRecebido).trim();
        if (!nome.isBlank() && !"outro".equalsIgnoreCase(nome)) {
            return nome;
        }
        if ("sticker".equals(tipoPayload) || "image/webp".equalsIgnoreCase(mimeType)) {
            return "figurinha.webp";
        }
        return tipoMedia.toLowerCase(Locale.ROOT);
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
