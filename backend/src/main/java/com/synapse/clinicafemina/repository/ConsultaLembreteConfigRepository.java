package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.ConsultaLembreteConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultaLembreteConfigRepository extends JpaRepository<ConsultaLembreteConfig, Long> {

    List<ConsultaLembreteConfig> findByClinicaIdOrderByNomeAsc(Long clinicaId);

    Optional<ConsultaLembreteConfig> findByIdAndClinicaId(Long id, Long clinicaId);
}
