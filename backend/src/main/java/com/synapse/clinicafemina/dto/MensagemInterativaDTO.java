package com.synapse.clinicafemina.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Conteudo interativo normalizado para historico, sem expor o payload bruto do provider. */
public record MensagemInterativaDTO(
        @NotBlank
        @Pattern(regexp = "BOTOES|LISTA", message = "tipo deve ser BOTOES ou LISTA")
        String tipo,
        @Size(max = 80)
        String textoAcao,
        @NotEmpty
        @Size(max = 10)
        List<@Valid OpcaoDTO> opcoes
) {
    @JsonIgnore
    @AssertTrue(message = "mensagens BOTOES aceitam no maximo 3 opcoes")
    public boolean isQuantidadeValida() {
        return opcoes == null || !"BOTOES".equals(tipo) || opcoes.size() <= 3;
    }

    public record OpcaoDTO(
            @NotBlank @Size(max = 200) String id,
            @NotBlank @Size(max = 200) String titulo,
            @Size(max = 200) String descricao
    ) {
    }
}
