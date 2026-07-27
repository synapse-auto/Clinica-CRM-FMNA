package com.synapse.clinicafemina.integration.whatsapp.uazap;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UazapProfilePhotoImageValidatorTest {

    private final UazapProfilePhotoImageValidator validator =
            new UazapProfilePhotoImageValidator();

    @Test
    void acceptsJpegPngAndWebpByMagicBytes() {
        assertThat(validator.validar(
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff},
                "image/jpeg"
        ).contentType()).isEqualTo("image/jpeg");
        assertThat(validator.validar(
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a},
                "image/png"
        ).contentType()).isEqualTo("image/png");
        assertThat(validator.validar(
                new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'},
                "image/webp"
        ).contentType()).isEqualTo("image/webp");
    }

    @Test
    void rejectsSvgEvenWhenDeclaredAsImage() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> validator.validar(svg, "image/svg+xml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("MAGIC_BYTES_INVALIDOS");
    }

    @Test
    void rejectsContentTypeThatDoesNotMatchMagicBytes() {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        assertThatThrownBy(() -> validator.validar(jpeg, "image/png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CONTENT_TYPE_DIVERGENTE");
    }

    @Test
    void rejectsMoreThanTwoMegabytes() {
        byte[] oversized = new byte[UazapProfilePhotoImageValidator.MAX_IMAGE_BYTES + 1];
        oversized[0] = (byte) 0xff;
        oversized[1] = (byte) 0xd8;
        oversized[2] = (byte) 0xff;
        assertThatThrownBy(() -> validator.validar(oversized, "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("IMAGEM_EXCEDE_LIMITE");
    }
}
