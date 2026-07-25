package com.synapse.clinicafemina.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Registro durável de idempotência para operações de escrita da Agenda expostas ao
 * N8N. Uma linha por (clinica, operação, idempotencyKey) — garantida por restrição
 * única no banco, não por cache em memória, para sobreviver a restart da aplicação.
 */
@Entity
@Table(name = "agenda_idempotency_key")
@Getter
@Setter
public class AgendaIdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    @Column(name = "operacao", nullable = false, length = 50)
    private String operacao;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "agendamento_local_id")
    private Long agendamentoLocalId;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm = OffsetDateTime.now();
}
