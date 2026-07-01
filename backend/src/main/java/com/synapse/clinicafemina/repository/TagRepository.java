package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByClinicaIdAndDeletadoEmIsNullOrderByNomeAsc(Long clinicaId);

    Optional<Tag> findByIdAndClinicaIdAndDeletadoEmIsNull(Long id, Long clinicaId);

    boolean existsByClinicaIdAndNomeIgnoreCaseAndDeletadoEmIsNull(Long clinicaId, String nome);

    boolean existsByClinicaIdAndNomeIgnoreCaseAndDeletadoEmIsNullAndIdNot(
            Long clinicaId,
            String nome,
            Long id
    );
}
