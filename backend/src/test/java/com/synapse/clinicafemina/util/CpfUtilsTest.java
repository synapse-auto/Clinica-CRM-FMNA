package com.synapse.clinicafemina.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CpfUtils")
class CpfUtilsTest {

    private static final String CPF_VALIDO = "11144477735";

    @Test
    @DisplayName("normalizarDigitos remove mascara e caracteres nao numericos")
    void normalizarDigitos_removesNonDigits() {
        assertThat(CpfUtils.normalizarDigitos("111.444.777-35")).isEqualTo(CPF_VALIDO);
        assertThat(CpfUtils.normalizarDigitos(null)).isEmpty();
    }

    @Test
    @DisplayName("valido aceita CPF com digitos verificadores corretos")
    void valido_acceptsCorrectCheckDigits() {
        assertThat(CpfUtils.valido(CPF_VALIDO)).isTrue();
    }

    @Test
    @DisplayName("valido rejeita CPF com todos os digitos iguais")
    void valido_rejectsAllSameDigits() {
        assertThat(CpfUtils.valido("11111111111")).isFalse();
    }

    @Test
    @DisplayName("valido rejeita CPF com quantidade de digitos incorreta")
    void valido_rejectsWrongLength() {
        assertThat(CpfUtils.valido("123")).isFalse();
        assertThat(CpfUtils.valido(null)).isFalse();
    }

    @Test
    @DisplayName("valido rejeita CPF com digito verificador incorreto")
    void valido_rejectsWrongCheckDigit() {
        assertThat(CpfUtils.valido("11144477736")).isFalse();
    }

    @Test
    @DisplayName("hashSeguro produz o mesmo hash para o mesmo CPF valido (determinismo)")
    void hashSeguro_isDeterministicForValidCpf() {
        String hash1 = CpfUtils.hashSeguro(CPF_VALIDO);
        String hash2 = CpfUtils.hashSeguro(CPF_VALIDO);
        assertThat(hash1).isNotNull().isEqualTo(hash2);
    }

    @Test
    @DisplayName("hashSeguro retorna null para CPF invalido (nunca gera hash de lixo)")
    void hashSeguro_returnsNullForInvalidCpf() {
        assertThat(CpfUtils.hashSeguro("123")).isNull();
    }

    @Test
    @DisplayName("mascarar oculta os 6 primeiros digitos, mantendo apenas os 5 finais")
    void mascarar_hidesFirstSixDigits() {
        assertThat(CpfUtils.mascarar(CPF_VALIDO)).isEqualTo("***.***.777-35");
    }

    @Test
    @DisplayName("mascarar retorna null para entrada com tamanho invalido")
    void mascarar_returnsNullForInvalidLength() {
        assertThat(CpfUtils.mascarar("123")).isNull();
    }

    @Test
    @DisplayName("formatarComMascara produz o formato exigido pela Darwin (000.000.000-00)")
    void formatarComMascara_producesDarwinFormat() {
        assertThat(CpfUtils.formatarComMascara(CPF_VALIDO)).isEqualTo("111.444.777-35");
    }
}
