package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.MensagemRapida;
import com.synapse.clinicafemina.domain.UsoMensagemRapida;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MensagemRapidaRepository extends JpaRepository<MensagemRapida, Long> {

    List<MensagemRapida> findByClinicaIdAndDeletadoEmIsNullOrderByTituloAsc(Long clinicaId);

    List<MensagemRapida> findByClinicaIdAndUsoInAndDeletadoEmIsNullOrderByTituloAsc(
            Long clinicaId, Collection<UsoMensagemRapida> usos);

    Optional<MensagemRapida> findByIdAndClinicaIdAndDeletadoEmIsNull(Long id, Long clinicaId);

    boolean existsByClinicaIdAndAtalhoIgnoreCaseAndDeletadoEmIsNull(Long clinicaId, String atalho);

    boolean existsByClinicaIdAndAtalhoIgnoreCaseAndDeletadoEmIsNullAndIdNot(
            Long clinicaId,
            String atalho,
            Long id
    );
}
