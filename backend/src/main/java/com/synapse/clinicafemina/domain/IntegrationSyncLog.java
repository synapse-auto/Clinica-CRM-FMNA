package com.synapse.clinicafemina.domain;

import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "integration_sync_log")
@Getter
@Setter
public class IntegrationSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    @Enumerated(EnumType.STRING)
    @Column(name = "external_provider", nullable = false, length = 20)
    private ExternalProviderType externalProvider;

    @Column(name = "iniciado_em", nullable = false, updatable = false)
    private OffsetDateTime iniciadoEm;

    @Column(name = "concluido_em")
    private OffsetDateTime concluidoEm;

    @Column(nullable = false, length = 20)
    private String status = "EXECUTANDO";

    @Column(name = "pacientes_processados", nullable = false)
    private Integer pacientesProcessados = 0;

    @Column(name = "pacientes_criados", nullable = false)
    private Integer pacientesCriados = 0;

    @Column(name = "pacientes_atualizados", nullable = false)
    private Integer pacientesAtualizados = 0;

    @Column(name = "agendamentos_processados", nullable = false)
    private Integer agendamentosProcessados = 0;

    @Column(name = "agendamentos_criados", nullable = false)
    private Integer agendamentosCriados = 0;

    @Column(name = "agendamentos_atualizados", nullable = false)
    private Integer agendamentosAtualizados = 0;

    @Column(name = "agendamentos_ignorados", nullable = false)
    private Integer agendamentosIgnorados = 0;

    @Column(name = "updated_after_utilizado")
    private OffsetDateTime updatedAfterUtilizado;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "mensagem_erro", columnDefinition = "TEXT")
    private String mensagemErro;

    @PrePersist
    protected void onCreate() {
        if (iniciadoEm == null) {
            iniciadoEm = OffsetDateTime.now();
        }
        if (status == null) {
            status = "EXECUTANDO";
        }
    }
}
