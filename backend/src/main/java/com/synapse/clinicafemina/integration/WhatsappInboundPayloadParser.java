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
        String tipo = texto(mensagem.get("type"));
        if (tipo.isBlank()) {
            tipo = "text";
        }
        tipo = tipo.toLowerCase(Locale.ROOT);
        if ("text".equals(tipo)) {
            Map<String, Object> texto = (Map<String, Object>) mensagem.get("text");
            String conteudo = texto == null ? "" : String.valueOf(texto.getOrDefault("body", ""));
            return new DadosMensagem(
                    "TEXTO",
                    conteudo,
                    null,
                    "text/plain",
                    null,
                    "#reset".equalsIgnoreCase(conteudo.trim())
            );
        }

        if ("reaction".equals(tipo)) {
            Map<String, Object> reaction = (Map<String, Object>) mensagem.get("reaction");
            String emoji = reaction == null ? "" : texto(reaction.get("emoji"));
            return new DadosMensagem(
                    "TEXTO",
                    emoji.isBlank() ? "Paciente removeu uma reação" : "Paciente reagiu com " + emoji,
                    null,
                    "text/plain",
                    null,
                    false
            );
        }

        if ("button".equals(tipo)) {
            Map<String, Object> button = (Map<String, Object>) mensagem.get("button");
            return mensagemTextoInterativa(button, false);
        }

        if ("interactive".equals(tipo)) {
            Map<String, Object> interactive = (Map<String, Object>) mensagem.get("interactive");
            return extrairRespostaInterativa(interactive);
        }

        Map<String, Object> media = objetoMedia(mensagem, tipo);
        String mediaId = primeiroTexto(media, "id", "media_id", "mediaId");
        if (mediaId.isBlank()) {
            mediaId = primeiroTexto(mensagem, "media_id", "mediaId");
        }
        if (media == null && !mediaId.isBlank()) {
            media = Map.of();
        }
        if (media == null || mediaId.isBlank()) {
            return new DadosMensagem(
                    "OUTRO",
                    conteudoGenerico(tipo),
                    null,
                    "application/octet-stream",
                    null,
                    false
            );
        }
        String tipoMedia = mapearTipoMedia(tipo);
        String mimeType = primeiroTexto(media, "mime_type", "mimeType", "mimetype");
        if (mimeType.isBlank()) {
            mimeType = primeiroTexto(mensagem, "mime_type", "mimeType", "mimetype");
        }
        if (mimeType.isBlank()) {
            mimeType = mimePadrao(tipoMedia, tipo);
        }
        Object nomeRecebido = media.get("filename");
        if (nomeRecebido == null) {
            nomeRecebido = media.get("file_name");
        }
        if (nomeRecebido == null) {
            nomeRecebido = media.get("fileName");
        }
        String nome = normalizarNomeArquivo(nomeRecebido, tipo, tipoMedia, mimeType);
        String legenda = texto(media.get("caption"));
        String conteudo = legenda.isBlank() ? descricaoMidia(tipo, tipoMedia, nome) : legenda;
        return new DadosMensagem(
                tipoMedia,
                conteudo,
                mediaId,
                mimeType,
                nome,
                false
        );
    }

    public String normalizarTelefone(String telefone) {
        return WhatsappPhoneNormalizer.normalize(telefone);
    }

    /**
     * UAZAPI documenta o objeto de mídia como {@code image.id}. Para tolerar payloads compatíveis
     * de versões/rotas que usem {@code media_id}, {@code mediaId} ou um envelope {@code media}, a
     * normalização é deliberadamente limitada a essas chaves estruturais e mantém o contrato Meta.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> objetoMedia(Map<String, Object> mensagem, String tipo) {
        Object valor = mensagem.get(tipo);
        if (!(valor instanceof Map<?, ?>)) {
            valor = mensagem.get("media");
        }
        if (!(valor instanceof Map<?, ?> mapa)) {
            return null;
        }
        Map<String, Object> resultado = new java.util.LinkedHashMap<>();
        mapa.forEach((chave, item) -> resultado.put(String.valueOf(chave), item));
        return resultado;
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
            case "video" -> "VIDEO";
            default -> "OUTRO";
        };
    }

    private String conteudoGenerico(String tipo) {
        return switch (tipo == null ? "" : tipo.toLowerCase(Locale.ROOT)) {
            case "location" -> "Localização recebida";
            case "contacts", "contact" -> "Contato compartilhado";
            case "video" -> "Vídeo recebido";
            case "order" -> "Pedido recebido";
            case "button" -> "Resposta de botão não identificada";
            case "interactive" -> "Resposta interativa não identificada";
            case "reaction" -> "Reação não identificada";
            default -> "Tipo de mensagem ainda não suportado";
        };
    }

    private DadosMensagem mensagemTextoInterativa(Map<String, Object> resposta, boolean lista) {
        String titulo = resposta == null ? "" : primeiroTexto(resposta, "title", "text");
        String descricao = resposta == null ? "" : texto(resposta.get("description"));
        String fallbackTecnico = resposta == null ? "" : primeiroTexto(resposta, "payload", "id");
        String conteudo = titulo.isBlank() ? fallbackTecnico : titulo;
        if (lista && !titulo.isBlank() && !descricao.isBlank()) {
            conteudo += "\n" + descricao;
        }
        if (conteudo.isBlank()) {
            conteudo = lista ? "Resposta de lista não identificada" : "Resposta de botão não identificada";
        }
        return new DadosMensagem("TEXTO", conteudo, null, "text/plain", null, false);
    }

    @SuppressWarnings("unchecked")
    private DadosMensagem extrairRespostaInterativa(Map<String, Object> interactive) {
        if (interactive == null) {
            return new DadosMensagem("TEXTO", "Resposta interativa não identificada", null, "text/plain", null, false);
        }
        String subtipo = texto(interactive.get("type")).toLowerCase(Locale.ROOT);
        return switch (subtipo) {
            case "button_reply" -> mensagemTextoInterativa((Map<String, Object>) interactive.get("button_reply"), false);
            case "list_reply" -> mensagemTextoInterativa((Map<String, Object>) interactive.get("list_reply"), true);
            default -> new DadosMensagem("TEXTO", "Resposta interativa não identificada", null, "text/plain", null, false);
        };
    }

    private String descricaoMidia(String tipoPayload, String tipoMedia, String nome) {
        return switch (tipoPayload) {
            case "image" -> "Imagem recebida";
            case "sticker" -> "Figurinha recebida";
            case "audio" -> "Áudio recebido";
            case "document" -> "Documento recebido";
            case "video" -> "Vídeo recebido";
            default -> tipoMedia.equals("OUTRO") ? conteudoGenerico(tipoPayload) : nome;
        };
    }

    private String primeiroTexto(Map<String, Object> origem, String... chaves) {
        if (origem == null) {
            return "";
        }
        for (String chave : chaves) {
            String valor = texto(origem.get(chave));
            if (!valor.isBlank()) {
                return valor;
            }
        }
        return "";
    }

    private String texto(Object valor) {
        if (valor == null) {
            return "";
        }
        String texto = String.valueOf(valor).trim();
        return "null".equalsIgnoreCase(texto) ? "" : texto;
    }

    private String mimePadrao(String tipoMedia, String tipoPayload) {
        if ("sticker".equals(tipoPayload)) {
            return "image/webp";
        }
        return switch (tipoMedia) {
            case "IMAGEM" -> "image/jpeg";
            case "AUDIO" -> "audio/ogg";
            case "VIDEO" -> "video/mp4";
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
            String nomeArquivo,
            boolean comandoReset
    ) {
    }
}
