package com.synapse.clinicafemina.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("GESTOR")
public class Gestor extends Usuario {
}
