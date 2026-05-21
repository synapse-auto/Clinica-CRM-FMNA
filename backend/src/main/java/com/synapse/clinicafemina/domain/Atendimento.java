package com.synapse.clinicafemina.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity
@Table(name = "atendimento")
@Getter
@Setter
public class Atendimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendente_principal_id")
    private Usuario atendentePrincipal;

    @Column(name = "data_inicio", nullable = false)
    private OffsetDateTime dataInicio;

    @Column(name = "data_encerramento")
    private OffsetDateTime dataEncerramento;

    @Column(nullable = false, length = 20)
    private String status = "ATIVO";

    @Column(name = "tratado_por_ia", nullable = false)
    private Boolean tratadoPorIa = false;

    @Column(name = "motivo_encerramento", length = 255)
    private String motivoEncerramento;

    @Column(name = "ultima_mensagem_em")
    private OffsetDateTime ultimaMensagemEm;

    @Column(name = "nao_lidas", nullable = false)
    private Integer naoLidas = 0;

    @Column(name = "whatsapp_chat_id", length = 50)
    private String whatsappChatId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @PrePersist
    protected void onCreate() {
        if (dataInicio == null) dataInicio = OffsetDateTime.now();
        criadoEm = OffsetDateTime.now();
        atualizadoEm = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoEm = OffsetDateTime.now();
    }
}
