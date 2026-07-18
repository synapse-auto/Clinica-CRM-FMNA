package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Clinica;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClinicaRepository extends JpaRepository<Clinica, Long> {

    Optional<Clinica> findBySlug(String slug);

    Optional<Clinica> findByWhatsappPhoneNumberId(String whatsappPhoneNumberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Clinica c WHERE c.id = :id")
    Optional<Clinica> findByIdForUpdate(@Param("id") Long id);
}
