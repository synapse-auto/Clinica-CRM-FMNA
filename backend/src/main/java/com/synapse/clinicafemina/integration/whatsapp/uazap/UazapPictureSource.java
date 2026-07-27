package com.synapse.clinicafemina.integration.whatsapp.uazap;

import java.net.URI;
import java.util.Arrays;

public record UazapPictureSource(
        Type type,
        URI url,
        byte[] bytes,
        String contentType
) {
    public enum Type {
        URL,
        BYTES
    }

    public UazapPictureSource {
        bytes = bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }

    public static UazapPictureSource url(URI value) {
        return new UazapPictureSource(Type.URL, value, null, null);
    }

    public static UazapPictureSource bytes(byte[] value, String contentType) {
        return new UazapPictureSource(Type.BYTES, null, value, contentType);
    }
}
