package com.synapse.clinicafemina.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class EnviarTemplateWhatsappRequest {

    @NotBlank
    @Size(max = 512)
    private final String nome;

    @NotBlank
    @Size(max = 20)
    private final String idioma;

    @Valid
    @Size(max = 50)
    private final List<Parametro> parametros;

    @JsonCreator
    public EnviarTemplateWhatsappRequest(
            @JsonProperty("nome") String nome,
            @JsonProperty("idioma") String idioma,
            @JsonProperty("parametros") List<Parametro> parametros
    ) {
        this.nome = nome;
        this.idioma = idioma;
        this.parametros = parametros == null ? List.of() : List.copyOf(parametros);
    }

    @JsonAnySetter
    void rejeitarCampoDesconhecido(String nomeCampo, Object valor) {
        throw new IllegalArgumentException("Campo nao permitido: " + nomeCampo);
    }

    public String nome() {
        return nome;
    }

    public String idioma() {
        return idioma;
    }

    public List<Parametro> parametros() {
        return parametros;
    }

    public static final class Parametro {

        @NotBlank
        @Size(max = 20)
        private final String componente;

        @NotNull
        @Min(1)
        private final Integer posicao;

        @Min(0)
        private final Integer indiceBotao;

        @NotBlank
        @Size(max = 1024)
        private final String valor;

        @JsonCreator
        public Parametro(
                @JsonProperty("componente") String componente,
                @JsonProperty("posicao") Integer posicao,
                @JsonProperty("indiceBotao") Integer indiceBotao,
                @JsonProperty("valor") String valor
        ) {
            this.componente = componente;
            this.posicao = posicao;
            this.indiceBotao = indiceBotao;
            this.valor = valor;
        }

        @JsonAnySetter
        void rejeitarCampoDesconhecido(String nomeCampo, Object valorDesconhecido) {
            throw new IllegalArgumentException("Campo de parametro nao permitido: " + nomeCampo);
        }

        public String componente() {
            return componente;
        }

        public Integer posicao() {
            return posicao;
        }

        public Integer indiceBotao() {
            return indiceBotao;
        }

        public String valor() {
            return valor;
        }
    }
}
