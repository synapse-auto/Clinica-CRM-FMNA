package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.AtendimentoTag;
import com.synapse.clinicafemina.domain.AtendimentoTagId;
import com.synapse.clinicafemina.domain.Tag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AtendimentoTagRepository extends JpaRepository<AtendimentoTag, AtendimentoTagId> {

    @Query("""
            SELECT vinculo.tag
            FROM AtendimentoTag vinculo
            WHERE vinculo.atendimento.id = :atendimentoId
              AND vinculo.atendimento.clinica.id = :clinicaId
              AND vinculo.tag.clinica.id = :clinicaId
              AND vinculo.tag.deletadoEm IS NULL
            ORDER BY vinculo.tag.nome ASC
            """)
    List<Tag> findTagsByAtendimentoIdAndClinicaId(
            @Param("atendimentoId") Long atendimentoId,
            @Param("clinicaId") Long clinicaId
    );

    @Modifying
    @Query("""
            DELETE FROM AtendimentoTag vinculo
            WHERE vinculo.atendimento.id = :atendimentoId
              AND vinculo.tag.id = :tagId
              AND vinculo.atendimento.clinica.id = :clinicaId
              AND vinculo.tag.clinica.id = :clinicaId
            """)
    int deleteByAtendimentoIdAndTagIdAndClinicaId(
            @Param("atendimentoId") Long atendimentoId,
            @Param("tagId") Long tagId,
            @Param("clinicaId") Long clinicaId
    );
}
