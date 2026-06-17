package com.synapse.clinicafemina.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "consulta_lembrete_config")
@Getter
@Setter
public class ConsultaLembreteConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(nullable = false, length = 40)
    private String canal = "WHATSAPP";

    @Column(name = "antecedencia_quantidade", nullable = false)
    private Integer antecedenciaQuantidade;

    @Column(name = "antecedencia_unidade", nullable = false, length = 20)
    private String antecedenciaUnidade;

    @Column(name = "horario_envio")
    private LocalTime horarioEnvio;

    @Column(name = "permite_confirmacao", nullable = false)
    private Boolean permiteConfirmacao = true;

    @Column(name = "permite_cancelamento", nullable = false)
    private Boolean permiteCancelamento = true;

    @Column(name = "permite_reagendamento", nullable = false)
    private Boolean permiteReagendamento = true;

    @Column(name = "mensagem_template", columnDefinition = "TEXT")
    private String mensagemTemplate;

    @Column(name = "config_json", columnDefinition = "jsonb")
    private String configJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
