package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.FollowUpConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowUpConfigRepository extends JpaRepository<FollowUpConfig, Long> {

    List<FollowUpConfig> findByClinicaIdOrderByNomeAsc(Long clinicaId);

    Optional<FollowUpConfig> findByIdAndClinicaId(Long id, Long clinicaId);
}
