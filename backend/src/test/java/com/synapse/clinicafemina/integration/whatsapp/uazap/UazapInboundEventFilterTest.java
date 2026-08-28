package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UazapInboundEventFilter — origem de Status")
class UazapInboundEventFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UazapInboundEventFilter filter = new UazapInboundEventFilter();

    @ParameterizedTest
    @ValueSource(strings = {
            "remoteJid", "remote_jid", "chatId", "chatid", "from", "sender", "participant", "jid"
    })
    @DisplayName("reconhece status@broadcast nos campos semânticos de origem conhecidos")
    void recognizesStatusAcrossKnownOriginFields(String field) throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "%s":" STATUS@BROADCAST ",
                  "type":"image","image":{"id":"MEDIA-1"}
                }]}}]}]}
                """.formatted(field));

        assertThat(filter.deveIgnorar(payload)).isTrue();
    }

    @Test
    @DisplayName("reconhece key.remoteJid preservado do evento nativo")
    void recognizesNestedKeyRemoteJid() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "from":"5511988887777","key":{"remoteJid":"status@broadcast",
                  "participant":"5511988887777@s.whatsapp.net"},"type":"video"
                }]}}]}]}
                """);

        assertThat(filter.deveIgnorar(payload)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "5511988887777@s.whatsapp.net", "120363000000000000@g.us", "123456@newsletter"
    })
    @DisplayName("não bloqueia conversa privada, grupo ou canal sem regra explícita")
    void doesNotBlockOtherChatOrigins(String remoteJid) throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "key":{"remoteJid":"%s"},"type":"text"
                }]}}]}]}
                """.formatted(remoteJid));

        assertThat(filter.deveIgnorar(payload)).isFalse();
    }

    @Test
    @DisplayName("não filtra quando status@broadcast aparece apenas no conteúdo")
    void doesNotUseMessageContentAsSignal() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "from":"5511988887777","type":"text",
                  "text":{"body":"o identificador status@broadcast apareceu aqui"}
                }]}}]}]}
                """);

        assertThat(filter.deveIgnorar(payload)).isFalse();
    }

    @Test
    @DisplayName("não confunde origem de mensagem citada com a origem atual do chat")
    void doesNotInspectQuotedMessageOrigin() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "from":"5511988887777","type":"text","context":{"quotedMessage":{
                    "key":{"remoteJid":"status@broadcast"}
                  }}
                }]}}]}]}
                """);

        assertThat(filter.deveIgnorar(payload)).isFalse();
    }
}
