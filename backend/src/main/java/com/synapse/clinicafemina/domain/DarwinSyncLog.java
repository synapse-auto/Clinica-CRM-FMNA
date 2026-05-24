package com.synapse.clinicafemina.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Registro de cada execução do job de sincronização com o Darwin.
 * Permite auditar histórico de sync, detectar falhas repetidas e
 * controlar o {@code updated_after} incremental.
 */
@Entity
@Table(name = "darwin_sync_log")
@Getter
@Setter
public class DarwinSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "iniciado_em", nullable = false, updatable = false)
    private OffsetDateTime iniciadoEm;

    @Column(name = "concluido_em")
    private OffsetDateTime concluidoEm;

    /**
     * Status: EXECUTANDO, SUCESSO, FALHA_PARCIAL, FALHA_TOTAL
     */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "pacientes_processados")
    private Integer pacientesProcessados = 0;

    @Column(name = "pacientes_criados")
    private Integer pacientesCriados = 0;

    @Column(name = "pacientes_atualizados")
    private Integer pacientesAtualizados = 0;

    @Column(name = "agendamentos_processados")
    private Integer agendamentosProcessados = 0;

    /** Timestamp Darwin usado como filtro `updated_after` neste run. */
    @Column(name = "updated_after_utilizado")
    private OffsetDateTime updatedAfterUtilizado;

    /** Mensagem de erro em caso de FALHA_TOTAL. */
    @Column(name = "mensagem_erro", columnDefinition = "TEXT")
    private String mensagemErro;

    @PrePersist
    protected void onCreate() {
        iniciadoEm = OffsetDateTime.now();
        if (status == null) status = "EXECUTANDO";
    }
}
