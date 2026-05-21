package com.synapse.clinicafemina.security.crypto;

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

@Component
@Converter
public class AesGcmConverter implements AttributeConverter<String, byte[]> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    private static byte[] masterKey;

    @Value("${app.crypto.master-key}")
    public void setMasterKey(String key) {
        if (key == null || key.length() != 32) {
            throw new IllegalArgumentException("Master key must be exactly 32 bytes for AES-256");
        }
        AesGcmConverter.masterKey = key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKey secretKey = new SecretKeySpec(masterKey, "AES");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec);
            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return byteBuffer.array();
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting data", e);
        }
    }

    @Override
    public String convertToEntityAttribute(byte[] dbData) {
        if (dbData == null || dbData.length == 0) {
            return null;
        }
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(dbData);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKey secretKey = new SecretKeySpec(masterKey, "AES");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);

            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);
            byte[] plainText = cipher.doFinal(cipherText);

            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting data", e);
        }
    }
}
