package com.synapse.clinicafemina.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "transferencia_atendimento")
@Getter
@Setter
public class TransferenciaAtendimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendimento_id", nullable = false)
    private Atendimento atendimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "de_usuario_id")
    private Usuario deUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "para_usuario_id", nullable = false)
    private Usuario paraUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transferido_por", nullable = false)
    private Usuario transferidoPor;

    @Column(length = 500)
    private String motivo;

    @Column(name = "transferido_em", nullable = false, updatable = false)
    private OffsetDateTime transferidoEm;

    @PrePersist
    protected void onCreate() {
        transferidoEm = OffsetDateTime.now();
    }
}
