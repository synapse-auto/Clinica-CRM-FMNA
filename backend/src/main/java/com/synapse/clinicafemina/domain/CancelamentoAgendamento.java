package com.synapse.clinicafemina.domain;

import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "cancelamento_agendamento")
@Getter
@Setter
public class CancelamentoAgendamento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "agendamento_id")
    private Agendamento agendamento;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "atendimento_id")
    private Atendimento atendimento;

    @Column(nullable = false, columnDefinition = "text") private String motivo;
    @Column(nullable = false, length = 40) private String origem;
    @Enumerated(EnumType.STRING) @Column(name = "external_provider", length = 20)
    private ExternalProviderType externalProvider;
    @Column(name = "external_agendamento_id", length = 120) private String externalAgendamentoId;
    @Column(name = "status_cancelamento", nullable = false, length = 30) private String statusCancelamento;
    @Column(name = "status_sincronizacao", nullable = false, length = 30) private String statusSincronizacao;
    @Column(name = "mensagem_erro_sincronizacao", length = 255) private String mensagemErroSincronizacao;
    @Column(name = "idempotency_key", length = 120) private String idempotencyKey;
    @Column(name = "coletado_em", nullable = false) private OffsetDateTime coletadoEm;
    @Column(name = "criado_em", nullable = false, updatable = false) private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false) private OffsetDateTime atualizadoEm;

    @PrePersist void created() { criadoEm = OffsetDateTime.now(); atualizadoEm = criadoEm; if (coletadoEm == null) coletadoEm = criadoEm; }
    @PreUpdate void updated() { atualizadoEm = OffsetDateTime.now(); }
}
