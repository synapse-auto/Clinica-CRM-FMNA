package com.synapse.clinicafemina.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("RECEPCIONISTA")
public class Recepcionista extends Usuario {
}
