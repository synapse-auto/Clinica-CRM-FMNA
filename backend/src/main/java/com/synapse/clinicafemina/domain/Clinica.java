package com.synapse.clinicafemina.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity
@Table(name = "clinica")
@Getter
@Setter
public class Clinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(name = "razao_social", nullable = false, length = 200)
    private String razaoSocial;

    @Column(nullable = false, length = 18, unique = true)
    private String cnpj;

    @Column(name = "email_contato", nullable = false)
    private String emailContato;

    @Column(name = "telefone_contato", nullable = false, length = 20)
    private String telefoneContato;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "cor_primaria", length = 7)
    private String corPrimaria;

    @Column(name = "tema_padrao", length = 10)
    private String temaPadrao = "CLARO";

    @Column(name = "fuso_horario", nullable = false, length = 60)
    private String fusoHorario = "America/Sao_Paulo";

    @Column(name = "ia_24h", nullable = false)
    private Boolean ia24h = false;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @PrePersist
    protected void onCreate() {
        criadoEm = OffsetDateTime.now();
        atualizadoEm = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoEm = OffsetDateTime.now();
    }
}
