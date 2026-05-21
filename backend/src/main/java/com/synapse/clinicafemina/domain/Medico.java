package com.synapse.clinicafemina.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("MEDICO")
public class Medico extends Usuario {
}
