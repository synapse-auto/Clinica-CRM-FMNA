package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.MensagemFestivaConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MensagemFestivaConfigRepository extends JpaRepository<MensagemFestivaConfig, Long> {

    List<MensagemFestivaConfig> findByClinicaIdOrderByMesDiaAscNomeAsc(Long clinicaId);

    Optional<MensagemFestivaConfig> findByIdAndClinicaId(Long id, Long clinicaId);
}
