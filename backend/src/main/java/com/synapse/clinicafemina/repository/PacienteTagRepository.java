package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.PacienteTag;
import com.synapse.clinicafemina.domain.PacienteTagId;
import com.synapse.clinicafemina.domain.Tag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PacienteTagRepository extends JpaRepository<PacienteTag, PacienteTagId> {

    @Query("""
            SELECT vinculo.tag
            FROM PacienteTag vinculo
            WHERE vinculo.paciente.id = :pacienteId
              AND vinculo.paciente.clinica.id = :clinicaId
              AND vinculo.tag.clinica.id = :clinicaId
              AND vinculo.tag.deletadoEm IS NULL
            ORDER BY vinculo.tag.nome ASC
            """)
    List<Tag> findTagsByPacienteIdAndClinicaId(
            @Param("pacienteId") Long pacienteId,
            @Param("clinicaId") Long clinicaId
    );

    @Modifying
    @Query("""
            DELETE FROM PacienteTag vinculo
            WHERE vinculo.paciente.id = :pacienteId
              AND vinculo.tag.id = :tagId
              AND vinculo.paciente.clinica.id = :clinicaId
              AND vinculo.tag.clinica.id = :clinicaId
            """)
    int deleteByPacienteIdAndTagIdAndClinicaId(
            @Param("pacienteId") Long pacienteId,
            @Param("tagId") Long tagId,
            @Param("clinicaId") Long clinicaId
    );
}
