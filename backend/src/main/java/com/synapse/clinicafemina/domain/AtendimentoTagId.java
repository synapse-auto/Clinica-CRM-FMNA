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
public class AtendimentoTagId implements Serializable {

    @Column(name = "atendimento_id")
    private Long atendimentoId;

    @Column(name = "tag_id")
    private Long tagId;
}
