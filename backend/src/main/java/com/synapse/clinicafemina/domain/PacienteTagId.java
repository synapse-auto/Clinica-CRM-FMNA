package com.synapse.clinicafemina.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PacienteTagId implements Serializable {

    @Column(name = "paciente_id")
    private Long pacienteId;

    @Column(name = "tag_id")
    private Long tagId;
}
