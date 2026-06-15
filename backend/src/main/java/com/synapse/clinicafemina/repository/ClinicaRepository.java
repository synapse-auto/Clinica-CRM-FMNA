package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Clinica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClinicaRepository extends JpaRepository<Clinica, Long> {

    Optional<Clinica> findBySlug(String slug);

    Optional<Clinica> findByWhatsappPhoneNumberId(String whatsappPhoneNumberId);
}
