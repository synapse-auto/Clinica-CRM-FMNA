package com.synapse.clinicafemina.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utilitários de CPF compartilhados pelo fluxo de Agenda provider-agnostic.
 * O algoritmo de hash replica exatamente {@code ExternalSyncTransactionService.gerarSha256}
 * para que o {@code cpfHash} calculado aqui bata com o já persistido em {@code Paciente}
 * pelo pipeline de bulk sync existente (Medware). Não modifica nem reaproveita código
 * daquele arquivo diretamente, para não introduzir risco de regressão no Medware.
 */
public final class CpfUtils {

    private CpfUtils() {}

    public static String normalizarDigitos(String cpf) {
        return cpf == null ? "" : cpf.replaceAll("[^0-9]", "");
    }

    public static boolean valido(String cpfDigitos) {
        if (cpfDigitos == null || cpfDigitos.length() != 11 || cpfDigitos.chars().distinct().count() == 1) {
            return false;
        }
        return digitoVerificador(cpfDigitos, 9) == cpfDigitos.charAt(9) - '0'
                && digitoVerificador(cpfDigitos, 10) == cpfDigitos.charAt(10) - '0';
    }

    public static String hashSeguro(String cpfDigitos) {
        return valido(cpfDigitos) ? sha256(cpfDigitos) : null;
    }

    public static String mascarar(String cpfDigitos) {
        if (cpfDigitos == null || cpfDigitos.length() != 11) {
            return null;
        }
        return "***.***." + cpfDigitos.substring(6, 9) + "-" + cpfDigitos.substring(9, 11);
    }

    public static String formatarComMascara(String cpfDigitos) {
        return cpfDigitos.substring(0, 3) + "." + cpfDigitos.substring(3, 6) + "."
                + cpfDigitos.substring(6, 9) + "-" + cpfDigitos.substring(9, 11);
    }

    private static int digitoVerificador(String cpf, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (cpf.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = 11 - (soma % 11);
        return resto >= 10 ? 0 : resto;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(input.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
