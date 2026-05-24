package com.synapse.clinicafemina.security.crypto;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Converter JPA que criptografa/descriptografa dados sensíveis (LGPD) usando AES-256-GCM.
 *
 * Formato do blob no banco: [ IV (12 bytes) ][ CipherText + GCM Tag (16 bytes) ]
 *
 * IMPORTANTE: autoApply = false — o converter só é aplicado onde há {@code @Convert} explícito.
 */
@Component
@Converter(autoApply = false)
public class AesGcmConverter implements AttributeConverter<String, byte[]> {

    private static final String ALGORITHM   = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    @Value("${app.crypto.master-key}")
    private String rawKey;

    // Campo de instância (thread-safe via imutabilidade pós @PostConstruct)
    private SecretKey secretKey;

    @PostConstruct
    void init() {
        if (rawKey == null || rawKey.length() != 32) {
            throw new IllegalStateException(
                    "APP_ENCRYPTION_KEY_V1 deve ter exatamente 32 caracteres (AES-256). "
                    + "Verifique a variável de ambiente.");
        }
        this.secretKey = new SecretKeySpec(rawKey.getBytes(StandardCharsets.UTF_8), "AES");
        // Zeramos a String de referência (a JVM pode ainda mantê-la no heap,
        // mas pelo menos não fica acumulada no campo)
        rawKey = null;
    }

    @Override
    public byte[] convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            new SecureRandom().nextBytes(iv);   // IV único por operação

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            // Formato: IV || CipherText+Tag
            ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherText.length);
            buf.put(iv);
            buf.put(cipherText);
            return buf.array();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criptografar dado sensível", e);
        }
    }

    @Override
    public String convertToEntityAttribute(byte[] dbData) {
        if (dbData == null || dbData.length == 0) {
            return null;
        }
        // Mínimo: IV (12) + Tag GCM (16) = 28 bytes
        if (dbData.length < IV_LENGTH_BYTE + 16) {
            throw new IllegalArgumentException(
                    "Dado criptografado inválido: tamanho " + dbData.length + " bytes é insuficiente.");
        }
        try {
            ByteBuffer buf = ByteBuffer.wrap(dbData);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            buf.get(iv);

            byte[] cipherText = new byte[buf.remaining()];
            buf.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao descriptografar dado sensível", e);
        }
    }
}
