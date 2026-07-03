package com.synapse.clinicafemina.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "paciente_tag")
@Getter
@Setter
@NoArgsConstructor
public class PacienteTag {

    @EmbeddedId
    private PacienteTagId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("pacienteId")
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("tagId")
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    public PacienteTag(Paciente paciente, Tag tag) {
        this.paciente = paciente;
        this.tag = tag;
        this.id = new PacienteTagId(paciente.getId(), tag.getId());
    }

    @PrePersist
    void prePersist() {
        if (criadoEm == null) {
            criadoEm = OffsetDateTime.now();
        }
    }
}
