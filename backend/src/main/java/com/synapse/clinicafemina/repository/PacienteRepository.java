package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    /** Localiza paciente pelo número E.164 normalizado (usado na integração WhatsApp). */
    Optional<Paciente> findByClinicaIdAndTelefoneNormalizado(Long clinicaId, String telefoneNormalizado);
}
